package com.huginmunin.app.ui.onboarding

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huginmunin.app.R
import com.huginmunin.app.utils.LanguageManager
import com.huginmunin.app.utils.SurveyManager
import com.huginmunin.app.utils.UserManager

@Composable
fun OnboardingScreen(
    onCompleted: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    
    // Load saved step (persists across activity recreate)
    val savedStep = remember { LanguageManager.getOnboardingStep(context) }
    var currentStep by remember { mutableStateOf(savedStep) }
    
    // Profile Data State
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    
    // Language State
    val initialLanguage = remember { LanguageManager.getLanguage(context).ifEmpty { "ko" } }
    var selectedLanguage by remember { mutableStateOf(initialLanguage) }
    
    // Survey State (16 questions, 1-5 scale)
    val surveyResponses = remember { mutableStateMapOf<String, Int>() }
    var currentSurveyPage by remember { mutableStateOf(0) }
    
    // Consent States
    var consentTos by remember { mutableStateOf(false) }
    var consentPrivacy by remember { mutableStateOf(false) }
    var consentOverseas by remember { mutableStateOf(false) }
    var consentAccount by remember { mutableStateOf(false) }
    var consentLoan by remember { mutableStateOf(false) }
    var consentMarketing by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0F14))
    ) {
        when (currentStep) {
            0 -> {
                // Step 1: Language Selection
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LanguageSelectionStep(
                        selectedLanguage = selectedLanguage,
                        onLanguageSelected = { code -> 
                            selectedLanguage = code
                            LanguageManager.setLanguage(context, code)
                        },
                        onNext = {
                            LanguageManager.setOnboardingStep(context, 1)
                            activity?.recreate()
                        }
                    )
                }
            }
            1 -> {
                // Step 2: User Profile Setup
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ProfileSetupStep(
                        name = name,
                        onNameChange = { name = it },
                        phone = phone,
                        onPhoneChange = { phone = it },
                        dob = dob,
                        onDobChange = { dob = it },
                        onNext = {
                            UserManager.saveUserProfile(context, name, phone, dob)
                            LanguageManager.setOnboardingStep(context, 2)
                            currentStep = 2
                        }
                    )
                }
            }
            2 -> {
                // Step 3: Personality Survey
                SurveyStep(
                    currentPage = currentSurveyPage,
                    responses = surveyResponses,
                    onResponseChange = { questionId, score ->
                        surveyResponses[questionId] = score
                        SurveyManager.saveSurveyResponse(context, questionId, score)
                    },
                    onNextPage = { currentSurveyPage++ },
                    onPrevPage = { if (currentSurveyPage > 0) currentSurveyPage-- },
                    onComplete = {
                        LanguageManager.setOnboardingStep(context, 3)
                        currentStep = 3
                    }
                )
            }
            3 -> {
                // Step 4: Legal Consents
                ConsentStep(
                    consentTos = consentTos,
                    onConsentTosChange = { consentTos = it; SurveyManager.saveConsent(context, "terms_of_service", it) },
                    consentPrivacy = consentPrivacy,
                    onConsentPrivacyChange = { consentPrivacy = it; SurveyManager.saveConsent(context, "personal_info_collection", it) },
                    consentOverseas = consentOverseas,
                    onConsentOverseasChange = { consentOverseas = it; SurveyManager.saveConsent(context, "privacy_overseas", it) },
                    consentAccount = consentAccount,
                    onConsentAccountChange = { consentAccount = it; SurveyManager.saveConsent(context, "virtual_account", it) },
                    consentLoan = consentLoan,
                    onConsentLoanChange = { consentLoan = it; SurveyManager.saveConsent(context, "micro_loan", it) },
                    consentMarketing = consentMarketing,
                    onConsentMarketingChange = { consentMarketing = it; SurveyManager.saveConsent(context, "marketing", it) },
                    onComplete = {
                        LanguageManager.setOnboardingStep(context, 0) // Reset
                        LanguageManager.markOnboardingDone(context)
                        onCompleted()
                    }
                )
            }
        }
    }
}

@Composable
fun LanguageSelectionStep(
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit,
    onNext: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(R.string.onboarding_welcome),
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        Text(
            text = stringResource(R.string.onboarding_select_language),
            fontSize = 20.sp,
            color = Color(0xFFB0B0B0),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        LanguageOption("한국어", "ko", selectedLanguage == "ko") { onLanguageSelected("ko") }
        LanguageOption("English", "en", selectedLanguage == "en") { onLanguageSelected("en") }
        LanguageOption("日本語", "ja", selectedLanguage == "ja") { onLanguageSelected("ja") }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C63FF)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(stringResource(R.string.button_next), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ProfileSetupStep(
    name: String, onNameChange: (String) -> Unit,
    phone: String, onPhoneChange: (String) -> Unit,
    dob: String, onDobChange: (String) -> Unit,
    onNext: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.profile_title), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(stringResource(R.string.profile_subtitle), fontSize = 16.sp, color = Color(0xFFB0B0B0))
        
        Spacer(modifier = Modifier.height(8.dp))
        
        val textFieldColors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = Color(0xFF6C63FF),
            unfocusedBorderColor = Color(0xFF2A2D34),
            focusedLabelColor = Color(0xFF6C63FF),
            unfocusedLabelColor = Color(0xFFB0B0B0)
        )
        
        OutlinedTextField(
            value = name, onValueChange = onNameChange,
            label = { Text(stringResource(R.string.profile_name_label)) },
            placeholder = { Text(stringResource(R.string.profile_name_hint)) },
            modifier = Modifier.fillMaxWidth(),
            colors = textFieldColors, singleLine = true
        )
        
        OutlinedTextField(
            value = phone, onValueChange = onPhoneChange,
            label = { Text(stringResource(R.string.profile_phone_label)) },
            placeholder = { Text(stringResource(R.string.profile_phone_hint)) },
            modifier = Modifier.fillMaxWidth(),
            colors = textFieldColors, singleLine = true
        )
        
        OutlinedTextField(
            value = dob, onValueChange = onDobChange,
            label = { Text(stringResource(R.string.profile_dob_label)) },
            placeholder = { Text(stringResource(R.string.profile_dob_hint)) },
            modifier = Modifier.fillMaxWidth(),
            colors = textFieldColors, singleLine = true
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onNext,
            enabled = name.isNotEmpty() && phone.isNotEmpty() && dob.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF6C63FF),
                disabledContainerColor = Color(0xFF2A2D34)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(stringResource(R.string.button_next), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SurveyStep(
    currentPage: Int,
    responses: Map<String, Int>,
    onResponseChange: (String, Int) -> Unit,
    onNextPage: () -> Unit,
    onPrevPage: () -> Unit,
    onComplete: () -> Unit
) {
    val questionsPerPage = 4
    val totalQuestions = 16
    val totalPages = (totalQuestions + questionsPerPage - 1) / questionsPerPage
    
    val questionIds = (1..totalQuestions).map { "q$it" }
    val startIdx = currentPage * questionsPerPage
    val endIdx = minOf(startIdx + questionsPerPage, totalQuestions)
    val currentQuestions = questionIds.subList(startIdx, endIdx)
    
    val likertLabels = listOf(
        stringResource(R.string.likert_1),
        stringResource(R.string.likert_2),
        stringResource(R.string.likert_3),
        stringResource(R.string.likert_4),
        stringResource(R.string.likert_5)
    )
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Text(
            text = stringResource(R.string.survey_title),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = stringResource(R.string.survey_subtitle),
            fontSize = 14.sp,
            color = Color(0xFFB0B0B0)
        )
        
        // Progress
        Text(
            text = stringResource(R.string.survey_progress, currentPage + 1, totalPages),
            fontSize = 14.sp,
            color = Color(0xFF6C63FF),
            modifier = Modifier.padding(vertical = 16.dp)
        )
        
        LinearProgressIndicator(
            progress = { (currentPage + 1).toFloat() / totalPages },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = Color(0xFF6C63FF),
            trackColor = Color(0xFF2A2D34)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Questions
        currentQuestions.forEachIndexed { idx, questionId ->
            val questionText = getQuestionText(questionId)
            val currentScore = responses[questionId] ?: 0
            
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1D24)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "${startIdx + idx + 1}. $questionText",
                        fontSize = 16.sp,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        (1..5).forEach { score ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onResponseChange(questionId, score) }
                                    .padding(4.dp)
                            ) {
                                RadioButton(
                                    selected = currentScore == score,
                                    onClick = { onResponseChange(questionId, score) },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = Color(0xFF6C63FF),
                                        unselectedColor = Color(0xFF808080)
                                    )
                                )
                                Text(
                                    text = likertLabels[score - 1],
                                    fontSize = 10.sp,
                                    color = if (currentScore == score) Color(0xFF6C63FF) else Color(0xFF808080),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Navigation buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (currentPage > 0) {
                OutlinedButton(
                    onClick = onPrevPage,
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("이전")
                }
            }
            
            Button(
                onClick = {
                    if (currentPage < totalPages - 1) onNextPage()
                    else onComplete()
                },
                enabled = currentQuestions.all { (responses[it] ?: 0) > 0 },
                modifier = Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6C63FF),
                    disabledContainerColor = Color(0xFF2A2D34)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    if (currentPage < totalPages - 1) stringResource(R.string.button_next) 
                    else stringResource(R.string.button_next),
                    fontSize = 16.sp, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun getQuestionText(questionId: String): String {
    return when (questionId) {
        "q1" -> stringResource(R.string.survey_q1)
        "q2" -> stringResource(R.string.survey_q2)
        "q3" -> stringResource(R.string.survey_q3)
        "q4" -> stringResource(R.string.survey_q4)
        "q5" -> stringResource(R.string.survey_q5)
        "q6" -> stringResource(R.string.survey_q6)
        "q7" -> stringResource(R.string.survey_q7)
        "q8" -> stringResource(R.string.survey_q8)
        "q9" -> stringResource(R.string.survey_q9)
        "q10" -> stringResource(R.string.survey_q10)
        "q11" -> stringResource(R.string.survey_q11)
        "q12" -> stringResource(R.string.survey_q12)
        "q13" -> stringResource(R.string.survey_q13)
        "q14" -> stringResource(R.string.survey_q14)
        "q15" -> stringResource(R.string.survey_q15)
        "q16" -> stringResource(R.string.survey_q16)
        else -> ""
    }
}

@Composable
fun ConsentStep(
    consentTos: Boolean, onConsentTosChange: (Boolean) -> Unit,
    consentPrivacy: Boolean, onConsentPrivacyChange: (Boolean) -> Unit,
    consentOverseas: Boolean, onConsentOverseasChange: (Boolean) -> Unit,
    consentAccount: Boolean, onConsentAccountChange: (Boolean) -> Unit,
    consentLoan: Boolean, onConsentLoanChange: (Boolean) -> Unit,
    consentMarketing: Boolean, onConsentMarketingChange: (Boolean) -> Unit,
    onComplete: () -> Unit
) {
    val requiredConsentsOk = consentTos && consentPrivacy && consentOverseas
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.consent_title),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = stringResource(R.string.consent_subtitle),
                fontSize = 14.sp,
                color = Color(0xFFB0B0B0),
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        
        // Agree All
        item {
            val allChecked = consentTos && consentPrivacy && consentOverseas && 
                            consentAccount && consentLoan && consentMarketing
            ConsentCheckbox(
                title = stringResource(R.string.consent_agree_all),
                checked = allChecked,
                onCheckedChange = {
                    onConsentTosChange(it)
                    onConsentPrivacyChange(it)
                    onConsentOverseasChange(it)
                    onConsentAccountChange(it)
                    onConsentLoanChange(it)
                    onConsentMarketingChange(it)
                },
                isHeader = true
            )
            Divider(color = Color(0xFF2A2D34), modifier = Modifier.padding(vertical = 8.dp))
        }
        
        // Required consents
        item {
            ConsentItem(
                title = stringResource(R.string.consent_tos_title),
                content = stringResource(R.string.consent_tos_content),
                checked = consentTos,
                onCheckedChange = onConsentTosChange,
                required = true
            )
        }
        
        item {
            ConsentItem(
                title = stringResource(R.string.consent_privacy_title),
                content = stringResource(R.string.consent_privacy_content),
                checked = consentPrivacy,
                onCheckedChange = onConsentPrivacyChange,
                required = true
            )
        }
        
        item {
            ConsentItem(
                title = stringResource(R.string.consent_overseas_title),
                content = stringResource(R.string.consent_overseas_content),
                checked = consentOverseas,
                onCheckedChange = onConsentOverseasChange,
                required = true
            )
        }
        
        // Optional consents
        item {
            ConsentItem(
                title = stringResource(R.string.consent_account_title),
                content = stringResource(R.string.consent_account_content),
                checked = consentAccount,
                onCheckedChange = onConsentAccountChange,
                required = false
            )
        }
        
        item {
            ConsentItem(
                title = stringResource(R.string.consent_loan_title),
                content = stringResource(R.string.consent_loan_content),
                checked = consentLoan,
                onCheckedChange = onConsentLoanChange,
                required = false
            )
        }
        
        item {
            ConsentItem(
                title = stringResource(R.string.consent_marketing_title),
                content = stringResource(R.string.consent_marketing_content),
                checked = consentMarketing,
                onCheckedChange = onConsentMarketingChange,
                required = false
            )
        }
        
        // Complete button
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onComplete,
                enabled = requiredConsentsOk,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6C63FF),
                    disabledContainerColor = Color(0xFF2A2D34)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.button_finish),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (requiredConsentsOk) Color.White else Color(0xFF808080)
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun ConsentCheckbox(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isHeader: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (checked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (checked) Color(0xFF6C63FF) else Color(0xFF808080),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            fontSize = if (isHeader) 18.sp else 16.sp,
            fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
            color = Color.White
        )
    }
}

@Composable
fun ConsentItem(
    title: String,
    content: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    required: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1D24)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCheckedChange(!checked) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (checked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (checked) Color(0xFF6C63FF) else Color(0xFF808080),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row {
                        Text(
                            text = title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (required) stringResource(R.string.consent_required) else stringResource(R.string.consent_optional),
                            fontSize = 12.sp,
                            color = if (required) Color(0xFFFF6B6B) else Color(0xFF808080)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    text = stringResource(R.string.consent_view_details),
                    fontSize = 14.sp,
                    color = Color(0xFF6C63FF)
                )
            }
            
            if (expanded) {
                Divider(color = Color(0xFF2A2D34), modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = stringResource(R.string.consent_scroll_hint),
                    fontSize = 12.sp,
                    color = Color(0xFF808080),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .background(Color(0xFF0D0F14), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = content,
                        fontSize = 14.sp,
                        color = Color(0xFFB0B0B0),
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

@Composable
fun LanguageOption(
    language: String,
    code: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Color(0xFF6C63FF) else Color(0xFF1A1D24),
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A2D34))
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = language,
                fontSize = 20.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = Color.White
            )
        }
    }
}

