package com.yggdrasil.assistant.storage

import android.content.Context
import android.os.StatFs
import android.util.Log
import java.io.File
import java.io.IOException

/**
 * [AppStorageVaultManager]
 * 앱 내부 저장소(Internal App Storage)를 용도별로 안전하게 분할 격리하고
 * 임시 파일, 화면 캡처 비트맵, 압축 ZIP 등의 수명주기(TTL)를 관리하여
 * 스마트폰 용량 잠식과 보안 누출을 원천 방지하는 스토리지 격리 볼트 관리자
 */
class AppStorageVaultManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "AppStorageVaultManager"

        // 디렉터리 명칭 상수
        private const val DIR_VAULT = "vault"              // SQLCipher DB, 키스토어, 프로필
        private const val DIR_MODELS = "models"            // EXAONE 3.0 GGUF 가중치 (~2.17GB)
        private const val DIR_SANDBOX = "sandbox"          // 에이전트 작업 공간, 자율 진화 패치
        private const val DIR_SCREENSHOTS = "screenshots"  // 화면 캡처 비트맵 캐시
        private const val DIR_ARCHIVES = "archives"        // 전송용 임시 ZIP 압축 캐시

        // 기본 수명주기(TTL) 밀리초
        const val TTL_SCREENSHOT_MS: Long = 60 * 60 * 1000L        // 스크린샷: 1시간
        const val TTL_ARCHIVE_MS: Long = 24 * 60 * 60 * 1000L      // 압축 아카이브: 24시간
        const val MIN_STORAGE_THRESHOLD_BYTES: Long = 500L * 1024 * 1024 // 안전 잔여 용량: 500MB

        @Volatile
        private var instance: AppStorageVaultManager? = null

        fun getInstance(context: Context): AppStorageVaultManager {
            return instance ?: synchronized(this) {
                instance ?: AppStorageVaultManager(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * 앱 내부의 격리된 전용 디렉터리 참조
     */
    val vaultDir: File by lazy { getOrCreateDir(File(context.filesDir, DIR_VAULT)) }
    val modelsDir: File by lazy { getOrCreateDir(File(context.filesDir, DIR_MODELS)) }
    val externalModelsDir: File? by lazy { context.getExternalFilesDir(DIR_MODELS) }
    val sandboxDir: File by lazy { getOrCreateDir(File(context.filesDir, DIR_SANDBOX)) }
    val screenshotCacheDir: File by lazy { getOrCreateDir(File(context.cacheDir, DIR_SCREENSHOTS)) }
    val archiveCacheDir: File by lazy { getOrCreateDir(File(context.cacheDir, DIR_ARCHIVES)) }

    init {
        // 싱글톤 초기화 시 디렉터리 구조 자동 생성 및 만료 캐시 1차 청소
        initializeStorageStructure()
        runGarbageCollection()
    }

    /**
     * 5대 격리 영역 디렉터리 구조 초기화
     */
    private fun initializeStorageStructure() {
        vaultDir
        modelsDir
        sandboxDir
        screenshotCacheDir
        archiveCacheDir
        Log.i(TAG, "앱 내부 격리 스토리지 볼트 초기화 완료: ${context.filesDir.absolutePath}")
    }

    private fun getOrCreateDir(dir: File): File {
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * 화면 캡처용 새 임시 파일 경로 발급
     */
    fun createScreenshotFile(fileNamePrefix: String = "screen_cap"): File {
        val fileName = "${fileNamePrefix}_${System.currentTimeMillis()}.png"
        return File(screenshotCacheDir, fileName)
    }

    /**
     * 임시 ZIP 압축용 새 캐시 파일 경로 발급
     */
    fun createArchiveCacheFile(archiveName: String): File {
        val safeName = if (archiveName.endsWith(".zip")) archiveName else "$archiveName.zip"
        return File(archiveCacheDir, safeName)
    }

    /**
     * 특정 스크린샷 파일 즉시 파기 (분석 완료 시 호출)
     */
    fun purgeScreenshot(file: File) {
        if (file.exists() && file.parentFile == screenshotCacheDir) {
            val deleted = file.delete()
            Log.d(TAG, "스크린샷 즉시 파기: ${file.name} (성공: $deleted)")
        }
    }

    /**
     * 샌드박스 작업 공간 정리 (에이전트 단일 세션 종료 후 호출)
     */
    fun clearSandbox() {
        val files = sandboxDir.listFiles() ?: return
        var deletedCount = 0
        for (file in files) {
            if (file.isFile && file.delete()) {
                deletedCount++
            }
        }
        Log.d(TAG, "샌드박스 임시 작업 공간 비우기 완료: $deletedCount 개 파일 삭제")
    }

    /**
     * 만료된 캐시 자동 소멸 가비지 컬렉터 (TTL 기반)
     * - 스크린샷: 1시간 경과 시 무조건 삭제
     * - 압축 파일: 24시간 경과 시 자동 회수
     */
    fun runGarbageCollection(): GcReport {
        val currentTime = System.currentTimeMillis()
        var freedBytes: Long = 0
        var deletedScreenshots = 0
        var deletedArchives = 0

        // 1. 스크린샷 캐시 청소
        screenshotCacheDir.listFiles()?.forEach { file ->
            if (currentTime - file.lastModified() > TTL_SCREENSHOT_MS) {
                freedBytes += file.length()
                if (file.delete()) deletedScreenshots++
            }
        }

        // 2. 압축 아카이브 캐시 청소
        archiveCacheDir.listFiles()?.forEach { file ->
            if (currentTime - file.lastModified() > TTL_ARCHIVE_MS) {
                freedBytes += file.length()
                if (file.delete()) deletedArchives++
            }
        }

        Log.i(TAG, "가비지 컬렉션 완료 - 스크린샷: $deletedScreenshots 개, 압축: $deletedArchives 개, 확보 용량: ${freedBytes / 1024} KB")
        return GcReport(deletedScreenshots, deletedArchives, freedBytes)
    }

    /**
     * 기기 스토리지 여유 공간 확인 및 부족 시 선제적 긴급 캐시 청소
     * @return 500MB 이상의 안전 용량이 확보되어 있으면 true, 미달이면 false
     */
    fun checkAndEnforceStorageQuota(): Boolean {
        val stat = StatFs(context.filesDir.path)
        val availableBytes = stat.availableBlocksLong * stat.blockSizeLong

        if (availableBytes < MIN_STORAGE_THRESHOLD_BYTES) {
            Log.w(TAG, "기기 내부 저장 용량 부족 감지 (${availableBytes / (1024 * 1024)} MB). 긴급 전체 캐시 일괄 소멸 실행!")
            // 긴급 청소: 스크린샷 및 아카이브 캐시 전원 삭제
            screenshotCacheDir.deleteRecursively()
            screenshotCacheDir.mkdirs()
            archiveCacheDir.deleteRecursively()
            archiveCacheDir.mkdirs()

            // 재측정
            val recheckStat = StatFs(context.filesDir.path)
            val recheckAvailable = recheckStat.availableBlocksLong * recheckStat.blockSizeLong
            return recheckAvailable >= MIN_STORAGE_THRESHOLD_BYTES
        }
        return true
    }

    /**
     * 전체 스토리지 사용 통계 보고서
     */
    fun getStorageUsageSummary(): Map<String, Long> {
        return mapOf(
            "vault_bytes" to getFolderSize(vaultDir),
            "models_bytes" to getFolderSize(modelsDir),
            "sandbox_bytes" to getFolderSize(sandboxDir),
            "screenshot_cache_bytes" to getFolderSize(screenshotCacheDir),
            "archive_cache_bytes" to getFolderSize(archiveCacheDir)
        )
    }

    private fun getFolderSize(folder: File): Long {
        var size: Long = 0
        folder.listFiles()?.forEach { file ->
            size += if (file.isDirectory) getFolderSize(file) else file.length()
        }
        return size
    }

    data class GcReport(
        val deletedScreenshots: Int,
        val deletedArchives: Int,
        val totalFreedBytes: Long
    )
}
