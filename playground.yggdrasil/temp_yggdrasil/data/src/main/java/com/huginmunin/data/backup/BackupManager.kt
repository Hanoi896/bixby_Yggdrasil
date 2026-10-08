package com.huginmunin.data.backup

import android.content.Context
import com.huginmunin.core.crypto.CryptoManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

sealed class BackupResult {
    data class Success(val path: String) : BackupResult()
    data class Error(val message: String) : BackupResult()
}

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cryptoManager: CryptoManager
) {

    suspend fun createEncryptedBackup(): BackupResult = withContext(Dispatchers.IO) {
        try {
            val dbFile = context.getDatabasePath("hugin_munin.db")
            
            // Validate database file
            if (!dbFile.exists()) {
                return@withContext BackupResult.Error("Database file not found")
            }
            
            if (!dbFile.canRead()) {
                return@withContext BackupResult.Error("Database file not accessible")
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val backupFileName = "backup_huginmunin_$timestamp.enc"
            val backupFile = File(context.filesDir, backupFileName)

            // Read and encrypt database
            val dbBytes = try {
                dbFile.readBytes()
            } catch (e: IOException) {
                return@withContext BackupResult.Error("Failed to read database: ${e.message}")
            }

            val encryptedBytes = try {
                cryptoManager.encrypt(dbBytes)
            } catch (e: Exception) {
                return@withContext BackupResult.Error("Failed to encrypt backup: ${e.message}")
            }

            // Write encrypted backup
            try {
                backupFile.writeBytes(encryptedBytes)
            } catch (e: IOException) {
                return@withContext BackupResult.Error("Failed to write backup file: ${e.message}")
            }

            return@withContext BackupResult.Success(backupFile.absolutePath)
        } catch (e: Exception) {
            return@withContext BackupResult.Error("Unexpected error: ${e.message}")
        }
    }
}
