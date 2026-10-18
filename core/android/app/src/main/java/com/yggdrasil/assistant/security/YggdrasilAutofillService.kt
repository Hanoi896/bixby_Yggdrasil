package com.yggdrasil.assistant.security

import android.app.assist.AssistStructure
import android.content.Context
import android.os.CancellationSignal
import android.service.autofill.*
import android.util.Base64
import android.util.Log
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews

/**
 * [YggdrasilAutofillService]
 * 안드로이드 시스템 공식 AutofillService 상속 자동 로그인 프레임워크.
 * AI가 비인가 방식으로 화면을 터치하는 대신, OS 공식 자동완성 파이프라인을 통해
 * 암호화 볼트에 저장된 실제 계정 정보를 안전하게 주입합니다.
 */
class YggdrasilAutofillService : AutofillService() {

    companion object {
        private const val TAG = "YggdrasilAutofill"
        private const val PREFS_NAME = "yggdrasil_autofill_vault"
    }

    private val vault by lazy { BiometricCredentialVault.getInstance(this) }
    private val prefs by lazy { getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val structure = request.fillContexts.lastOrNull()?.structure ?: run {
            callback.onSuccess(null)
            return
        }

        val targetPackage = structure.activityComponent?.packageName ?: ""
        val fields = mutableMapOf<String, AutofillId>()
        traverseStructure(structure.getWindowNodeAt(0).rootViewNode, fields)

        val usernameId = fields["username"]
        val passwordId = fields["password"]

        if (usernameId == null || passwordId == null) {
            callback.onSuccess(null)
            return
        }

        // 실제 볼트에 저장된 자격증명 조회 (Zero-Mock: 저장된 계정이 없을 시 null 반환)
        val savedEncrypted = prefs.getString("cred_$targetPackage", null)
        if (savedEncrypted == null) {
            callback.onSuccess(null)
            return
        }

        try {
            val parts = savedEncrypted.split(":")
            if (parts.size != 3) {
                callback.onSuccess(null)
                return
            }
            val username = parts[0]
            val iv = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipherText = Base64.decode(parts[2], Base64.NO_WRAP)
            val decryptedPassword = vault.decryptCredential(BiometricCredentialVault.EncryptedPayload(iv, cipherText))

            val responseBuilder = FillResponse.Builder()
            val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
                setTextViewText(android.R.id.text1, "이그드라실 보안 자동 로그인 ($username)")
            }

            val dataset = Dataset.Builder()
                .setValue(usernameId, AutofillValue.forText(username), presentation)
                .setValue(passwordId, AutofillValue.forText(decryptedPassword), presentation)
                .build()

            responseBuilder.addDataset(dataset)

            val saveInfo = SaveInfo.Builder(
                SaveInfo.SAVE_DATA_TYPE_PASSWORD,
                arrayOf(usernameId, passwordId)
            ).build()
            responseBuilder.setSaveInfo(saveInfo)

            callback.onSuccess(responseBuilder.build())
        } catch (e: Exception) {
            Log.e(TAG, "볼트 자격증명 복호화 및 주입 실패", e)
            callback.onSuccess(null)
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure ?: run {
            callback.onSuccess()
            return
        }

        val targetPackage = structure.activityComponent?.packageName ?: ""
        val values = mutableMapOf<String, String>()
        extractValuesFromStructure(structure.getWindowNodeAt(0).rootViewNode, values)

        val username = values["username"]
        val password = values["password"]

        if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
            try {
                val encrypted = vault.encryptCredential(password)
                val ivBase64 = Base64.encodeToString(encrypted.iv, Base64.NO_WRAP)
                val cipherBase64 = Base64.encodeToString(encrypted.cipherText, Base64.NO_WRAP)
                val record = "$username:$ivBase64:$cipherBase64"
                prefs.edit().putString("cred_$targetPackage", record).apply()
                Log.i(TAG, "[$targetPackage] 패키지 계정 자격증명 암호화 볼트 저장 성공")
            } catch (e: Exception) {
                Log.e(TAG, "자격증명 암호화 저장 실패", e)
            }
        }

        callback.onSuccess()
    }

    private fun traverseStructure(node: AssistStructure.ViewNode, fields: MutableMap<String, AutofillId>) {
        val hints = node.autofillHints
        if (hints != null) {
            for (hint in hints) {
                if (hint.equals(android.view.View.AUTOFILL_HINT_USERNAME, ignoreCase = true) ||
                    hint.equals(android.view.View.AUTOFILL_HINT_EMAIL_ADDRESS, ignoreCase = true)
                ) {
                    node.autofillId?.let { fields["username"] = it }
                } else if (hint.equals(android.view.View.AUTOFILL_HINT_PASSWORD, ignoreCase = true)) {
                    node.autofillId?.let { fields["password"] = it }
                }
            }
        }

        for (i in 0 until node.childCount) {
            traverseStructure(node.getChildAt(i), fields)
        }
    }

    private fun extractValuesFromStructure(node: AssistStructure.ViewNode, values: MutableMap<String, String>) {
        val hints = node.autofillHints
        val text = node.text?.toString()
        if (hints != null && !text.isNullOrBlank()) {
            for (hint in hints) {
                if (hint.equals(android.view.View.AUTOFILL_HINT_USERNAME, ignoreCase = true) ||
                    hint.equals(android.view.View.AUTOFILL_HINT_EMAIL_ADDRESS, ignoreCase = true)
                ) {
                    values["username"] = text
                } else if (hint.equals(android.view.View.AUTOFILL_HINT_PASSWORD, ignoreCase = true)) {
                    values["password"] = text
                }
            }
        }

        for (i in 0 until node.childCount) {
            extractValuesFromStructure(node.getChildAt(i), values)
        }
    }
}
