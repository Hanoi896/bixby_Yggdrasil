package com.huginmunin.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huginmunin.core.model.Log
import com.huginmunin.core.model.Trustee
import com.huginmunin.data.entity.PermissionEntity
import com.huginmunin.core.repository.LogRepository
import com.huginmunin.data.repository.PermissionRepository
import com.huginmunin.core.repository.TrusteeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.huginmunin.core.privacy.LearningControlManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.huginmunin.app.worker.AutomationWorker

import com.huginmunin.app.assistant.CalendarManager
import com.huginmunin.app.assistant.SpeechManager
import com.huginmunin.ml.TextClassificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

import com.huginmunin.core.payment.PaymentManager
import com.huginmunin.core.security.BreakGlassManager
import com.huginmunin.data.backup.BackupManager
import com.huginmunin.data.backup.BackupResult
import com.huginmunin.app.assistant.CalendarResult
import com.huginmunin.app.munin.MuninRepository

@HiltViewModel
class MainViewModel @Inject constructor(
    private val logRepository: LogRepository,
    private val permissionRepository: PermissionRepository,
    private val learningControlManager: LearningControlManager,
    private val workManager: WorkManager,
    private val trusteeRepository: TrusteeRepository,
    private val paymentManager: PaymentManager,
    private val speechManager: SpeechManager,
    private val calendarManager: CalendarManager,
    private val textClassifier: TextClassificationHelper,
    private val breakGlassManager: BreakGlassManager,
    private val backupManager: BackupManager,
    private val muninRepository: MuninRepository
) : ViewModel() {

    val logs: StateFlow<List<Log>> = logRepository.getLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activePermissions: StateFlow<List<PermissionEntity>> = permissionRepository.getActivePermissions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trustees: StateFlow<List<Trustee>> = trusteeRepository.getTrustees()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Munin's Memories (Norse UI) - Safe with Flow
    val recentLogs = muninRepository.getRecentMemories()

    val isLearningPaused: StateFlow<Boolean> = learningControlManager.isLearningPaused
    val pauseEndTime: StateFlow<Long> = learningControlManager.pauseEndTime
    
    val isBreakGlassActive: StateFlow<Boolean> = breakGlassManager.isBreakGlassActive

    private val _chatMessages = MutableStateFlow<List<Pair<String, Boolean>>>(emptyList()) // Message, isUser
    val chatMessages = _chatMessages.asStateFlow()

    init {
        viewModelScope.launch {
            speechManager.speechResults.collect { text ->
                addUserMessage(text)
            }
        }
        viewModelScope.launch {
            textClassifier.init()
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        speechManager.shutdown()
    }

    fun toggleLearningPause(paused: Boolean, durationMs: Long = 0) {
        learningControlManager.setLearningPaused(paused, durationMs)
        val status = if (paused) "PAUSED (until ${durationMs/60000}m)" else "RESUMED"
        viewModelScope.launch {
            logRepository.logEvent("User", "Toggle Learning", status, "privacy")
        }
    }

    fun grantPermission(level: Int, scope: String) {
        viewModelScope.launch {
            try {
                // Default 1 hour duration for demo
                permissionRepository.grantPermission(level, scope, 3600000)
                logRepository.logEvent("User", "Grant Permission", "Level: $level, Scope: $scope", "permission")
            } catch (e: SecurityException) {
                logRepository.logEvent("User", "Grant Failed", "L$level: ${e.message}", "security_alert")
            } catch (e: Exception) {
                logRepository.logEvent("User", "Grant Error", e.message ?: "Unknown", "error")
            }
        }
    }

    fun revokePermission(id: Long) {
        viewModelScope.launch {
            permissionRepository.revokePermission(id)
            logRepository.logEvent("User", "Revoke Permission", "ID: $id", "permission")
        }
    }

    fun runAutomationNow() {
        val request = OneTimeWorkRequestBuilder<AutomationWorker>().build()
        workManager.enqueue(request)
    }

    fun addTrustee(name: String, relationship: String) {
        viewModelScope.launch {
            // In real app, we would exchange public keys here.
            val mockPublicKey = "MOCK_PUB_KEY_${System.currentTimeMillis()}"
            trusteeRepository.addTrustee(name, mockPublicKey, relationship)
            logRepository.logEvent("User", "Add Trustee", "Name: $name", "trustee")
        }
    }

    fun removeTrustee(id: Long) {
        viewModelScope.launch {
            trusteeRepository.removeTrustee(id)
            logRepository.logEvent("User", "Remove Trustee", "ID: $id", "trustee")
        }
    }

    fun simulatePayment(amount: Double, recipient: String) {
        viewModelScope.launch {
            val result = paymentManager.processPayment(amount, "USD", recipient)
            if (result.success) {
                logRepository.logEvent("Payment", "Success", "TX: ${result.transactionId}", "financial")
            } else {
                logRepository.logEvent("Payment", "Failed", result.message, "financial")
            }
        }
    }

    fun addUserMessage(text: String) {
        val current = _chatMessages.value.toMutableList()
        current.add(text to true)
        _chatMessages.value = current
        
        processUserIntent(text)
    }
    
    private fun processUserIntent(text: String) {
        viewModelScope.launch {
            // 1. Classify intent (Mock ML)
            val category = textClassifier.classify(text)
            
            // 2. Respond based on intent
            val response = when (category) {
                "Work" -> {
                    when (val result = calendarManager.getUpcomingEvents()) {
                        is CalendarResult.Success -> {
                            if (result.events.isNotEmpty()) {
                                "You have ${result.events.size} upcoming events. Next: ${result.events[0].title}"
                            } else {
                                "No upcoming work events found."
                            }
                        }
                        is CalendarResult.Error -> {
                            "Could not access calendar: ${result.message}"
                        }
                    }
                }
                "Priority" -> "I've noted that as urgent."
                else -> "I heard you say: $text (Category: $category)"
            }
            
            val current = _chatMessages.value.toMutableList()
            current.add(response to false)
            _chatMessages.value = current
            
            speechManager.speak(response)
        }
    }

    fun startListening() {
        speechManager.startListening()
    }

    fun activateBreakGlass(reason: String) {
        viewModelScope.launch {
            breakGlassManager.activateBreakGlass(reason)
        }
    }

    fun deactivateBreakGlass() {
        viewModelScope.launch {
            breakGlassManager.deactivateBreakGlass()
        }
    }

    fun createBackup() {
        viewModelScope.launch {
            when (val result = backupManager.createEncryptedBackup()) {
                is BackupResult.Success -> {
                    logRepository.logEvent("System", "Backup Created", "Path: ${result.path}", "backup")
                    addUserMessage("Backup created successfully at ${result.path}")
                }
                is BackupResult.Error -> {
                    logRepository.logEvent("System", "Backup Failed", result.message, "error")
                    addUserMessage("Backup failed: ${result.message}")
                }
            }
        }
    }
}
