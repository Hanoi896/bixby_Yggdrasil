package com.yggdrasil.assistant.nlp

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * [KoreanOntologyDatabase]
 * 한국어 행동-엔티티 온톨로지 온디바이스 데이터베이스 엔진.
 * 
 * 1. 인메모리 역색인(Inverted Index):
 *    - 구어체/축약어/은어 ➔ 표준 엔티티(앱/서비스)
 *    - 수많은 한국어 동사 활용형/어간 ➔ 표준 행동(Action)
 *    - 지시 대명사 ➔ 목표 카테고리(시각/기억 참조용)
 * 2. 원격 차분 업데이트(Delta Update) 지원:
 *    - 내부 저장소(filesDir/ontology/)의 최신 버전을 우선 탑재하고,
 *    - 없으면 APK의 assets/ontology/korean_action_ontology.json 기본 에셋 로드.
 */
class KoreanOntologyDatabase private constructor(private val context: Context) {

    companion object {
        private const val TAG = "KoreanOntologyDb"
        private const val ASSET_PATH = "ontology/korean_action_ontology.json"
        private const val OVERRIDE_FILENAME = "korean_action_ontology.json"

        @Volatile
        private var instance: KoreanOntologyDatabase? = null

        fun getInstance(context: Context): KoreanOntologyDatabase {
            return instance ?: synchronized(this) {
                instance ?: KoreanOntologyDatabase(context.applicationContext).also {
                    it.loadOntology()
                    instance = it
                }
            }
        }
    }

    data class VerbPattern(val action: KoreanSemanticLexicon.SemanticAction, val canonical: String, val stems: List<String>)
    data class EntityMapping(val canonicalApp: String, val category: String, val aliases: List<String>)
    data class DeviceControlMapping(val feature: String, val triggers: List<String>)

    // 인메모리 고속 룩업 테이블 (Trie / Inverted Index)
    private val aliasToCanonicalMap = ConcurrentHashMap<String, String>()
    private val aliasToCategoryMap = ConcurrentHashMap<String, String>()
    private val verbStemToActionMap = ConcurrentHashMap<String, KoreanSemanticLexicon.SemanticAction>()
    private val pronounList = mutableListOf<String>()
    private val deviceControlMap = ConcurrentHashMap<String, String>()

    var databaseVersion: String = "1.0.0"
        private set

    /**
     * 온톨로지 에셋 및 오버라이드 파일 로드
     */
    @Synchronized
    fun loadOntology() {
        try {
            val overrideFile = File(context.filesDir, "ontology/$OVERRIDE_FILENAME")
            val jsonStream: InputStream = if (overrideFile.exists()) {
                Log.i(TAG, "📦 [온톨로지 로드] 내부 저장소 차분 업데이트 파일 사용: ${overrideFile.absolutePath}")
                overrideFile.inputStream()
            } else {
                Log.i(TAG, "📦 [온톨로지 로드] APK assets 기본 온톨로지 사용: $ASSET_PATH")
                context.assets.open(ASSET_PATH)
            }

            val jsonContent = jsonStream.bufferedReader().use { it.readText() }
            parseOntologyJson(jsonContent)
        } catch (e: Exception) {
            Log.e(TAG, "온톨로지 파일 로드 실패", e)
        }
    }

    private fun parseOntologyJson(jsonStr: String) {
        val root = JSONObject(jsonStr)
        databaseVersion = root.optString("version", "1.0.0")

        aliasToCanonicalMap.clear()
        aliasToCategoryMap.clear()
        verbStemToActionMap.clear()
        pronounList.clear()
        deviceControlMap.clear()

        // 1. 동사 파싱
        val verbsArr = root.optJSONArray("verbs")
        if (verbsArr != null) {
            for (i in 0 until verbsArr.length()) {
                val vObj = verbsArr.getJSONObject(i)
                val actionStr = vObj.getString("action")
                val action = try {
                    KoreanSemanticLexicon.SemanticAction.valueOf(actionStr)
                } catch (_: Exception) {
                    KoreanSemanticLexicon.SemanticAction.GENERAL
                }
                val stemsArr = vObj.getJSONArray("stems")
                for (s in 0 until stemsArr.length()) {
                    val stem = stemsArr.getString(s).trim()
                    if (stem.isNotEmpty()) {
                        verbStemToActionMap[stem] = action
                    }
                }
            }
        }

        // 2. 명사 / 엔티티 파싱
        val entitiesArr = root.optJSONArray("entities")
        if (entitiesArr != null) {
            for (i in 0 until entitiesArr.length()) {
                val eObj = entitiesArr.getJSONObject(i)
                val canonical = eObj.getString("canonicalApp")
                val category = eObj.optString("category", "GENERAL")
                val aliasesArr = eObj.getJSONArray("aliases")
                for (a in 0 until aliasesArr.length()) {
                    val alias = aliasesArr.getString(a).trim()
                    if (alias.isNotEmpty()) {
                        aliasToCanonicalMap[alias] = canonical
                        aliasToCategoryMap[alias] = category
                    }
                }
                // 본명도 매핑에 포함
                aliasToCanonicalMap[canonical] = canonical
                aliasToCategoryMap[canonical] = category
            }
        }

        // 3. 지시 대명사 파싱
        val pronounsArr = root.optJSONArray("pronouns")
        if (pronounsArr != null) {
            for (i in 0 until pronounsArr.length()) {
                val pObj = pronounsArr.getJSONObject(i)
                val term = pObj.getString("term").trim()
                if (term.isNotEmpty()) {
                    pronounList.add(term)
                }
            }
        }

        // 4. 기기 제어 트리거 파싱
        val deviceArr = root.optJSONArray("deviceControls")
        if (deviceArr != null) {
            for (i in 0 until deviceArr.length()) {
                val dObj = deviceArr.getJSONObject(i)
                val feature = dObj.getString("feature")
                val triggersArr = dObj.getJSONArray("triggers")
                for (t in 0 until triggersArr.length()) {
                    val trigger = triggersArr.getString(t).trim()
                    if (trigger.isNotEmpty()) {
                        deviceControlMap[trigger] = feature
                    }
                }
            }
        }

        Log.i(TAG, "✅ [온톨로지 로드 완료 v$databaseVersion]: 어간 ${verbStemToActionMap.size}개, 엔티티 ${aliasToCanonicalMap.size}개, 대명사 ${pronounList.size}개 색인화")
    }

    /**
     * 원격 차분 업데이트 수신 시 새 온톨로지 파일 저장 및 즉각 갱신
     */
    @Synchronized
    fun updateOntologyDelta(newJsonContent: String): Boolean {
        return try {
            val dir = File(context.filesDir, "ontology")
            if (!dir.exists()) dir.mkdirs()
            val overrideFile = File(dir, OVERRIDE_FILENAME)
            overrideFile.writeText(newJsonContent)
            parseOntologyJson(newJsonContent)
            Log.i(TAG, "🎉 [온톨로지 차분 업데이트 성공] v$databaseVersion")
            true
        } catch (e: Exception) {
            Log.e(TAG, "온톨로지 차분 업데이트 실패", e)
            false
        }
    }

    // --- 인메모리 질의 인터페이스 ---

    fun getCanonicalEntity(alias: String): String? = aliasToCanonicalMap[alias]

    fun getAllAliases(): Map<String, String> = aliasToCanonicalMap

    fun getActionForStem(stem: String): KoreanSemanticLexicon.SemanticAction? = verbStemToActionMap[stem]

    fun getAllVerbStems(): Map<String, KoreanSemanticLexicon.SemanticAction> = verbStemToActionMap

    fun getPronouns(): List<String> = pronounList

    fun matchDeviceControl(text: String): String? {
        for ((trigger, feature) in deviceControlMap) {
            if (text.contains(trigger)) return feature
        }
        return null
    }
}
