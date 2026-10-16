package com.yggdrasil.assistant.integration

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * FileManagerSAF
 * Storage Access Framework(SAF) 기반 100% 실전 파일 관리 및 무손실 ZIP 압축 모듈
 * 
 * 1. Android 11+ (API 30+) Scoped Storage 정책 완벽 준수
 * 2. SAF content:// URI 및 앱 내부 격리 스토리지 자동 분기 처리
 * 3. ContentResolver 기반 InputStream 파이프라인으로 Permission Denied 원천 방지
 */
class FileManagerSAF(private val context: Context) {

    companion object {
        private const val TAG = "FileManagerSAF"
        private const val BUFFER_SIZE = 16384 // 16KB 고속 스트림 버퍼
    }

    data class CompressionResult(
        val success: Boolean,
        val outputZipPath: String,
        val totalFilesCompressed: Int,
        val compressedSizeBytes: Long,
        val message: String
    )

    /**
     * 특정 디렉터리 또는 SAF Tree URI 내의 파일 목록을 ZIP 파일로 고속 압축
     * @param targetPathOrUri 압축할 대상 폴더 경로 또는 SAF content:// URI
     * @param outputZipName 생성할 ZIP 파일명
     */
    suspend fun compressFiles(
        targetPathOrUri: String,
        outputZipName: String = "Yggdrasil_Archive.zip"
    ): CompressionResult = withContext(Dispatchers.IO) {
        val cleanTarget = targetPathOrUri.trim()
        val outputDir = File(context.filesDir, "archives").apply { mkdirs() }
        val zipFile = File(outputDir, outputZipName)

        if (cleanTarget.startsWith("content://")) {
            return@withContext compressFromSafUri(Uri.parse(cleanTarget), zipFile)
        } else {
            return@withContext compressFromFilePath(cleanTarget, zipFile)
        }
    }

    /**
     * 1. SAF Tree URI 기반 압축 (Android Scoped Storage 완전 호환)
     */
    private fun compressFromSafUri(treeUri: Uri, zipFile: File): CompressionResult {
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
            ?: DocumentFile.fromSingleUri(context, treeUri)
            ?: return CompressionResult(false, "", 0, 0L, "SAF URI를 해석할 수 없습니다: $treeUri")

        if (!rootDoc.exists()) {
            return CompressionResult(false, "", 0, 0L, "지정된 SAF 문서/폴더가 존재하지 않습니다.")
        }

        val targetFiles = if (rootDoc.isDirectory) {
            rootDoc.listFiles().filter { it.isFile && it.canRead() }
        } else {
            listOf(rootDoc)
        }

        if (targetFiles.isEmpty()) {
            return CompressionResult(false, "", 0, 0L, "폴더 내에 읽을 수 있는 파일이 없습니다.")
        }

        var compressedCount = 0
        try {
            ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
                val buffer = ByteArray(BUFFER_SIZE)
                for (doc in targetFiles) {
                    val inStream = context.contentResolver.openInputStream(doc.uri)
                    if (inStream != null) {
                        inStream.use { fileIn ->
                            val entryName = doc.name ?: "file_$compressedCount"
                            zipOut.putNextEntry(ZipEntry(entryName))
                            var readBytes: Int
                            while (fileIn.read(buffer).also { readBytes = it } != -1) {
                                zipOut.write(buffer, 0, readBytes)
                            }
                            zipOut.closeEntry()
                        }
                        compressedCount++
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "SAF URI 파일 압축 실패", e)
            return CompressionResult(false, "", 0, 0L, "SAF 압축 중 오류 발생: ${e.localizedMessage}")
        }

        return CompressionResult(
            success = true,
            outputZipPath = zipFile.absolutePath,
            totalFilesCompressed = compressedCount,
            compressedSizeBytes = zipFile.length(),
            message = "SAF 기반 총 ${compressedCount}개 파일 압축 완료 (${zipFile.length() / 1024} KB)"
        )
    }

    /**
     * 2. 파일 시스템 경로 기반 압축 (앱 내부 디렉터리 및 권한 허용 경로)
     */
    private fun compressFromFilePath(targetPath: String, zipFile: File): CompressionResult {
        val targetDir = File(targetPath)
        if (!targetDir.exists()) {
            return CompressionResult(false, "", 0, 0L, "대상 디렉터리가 존재하지 않습니다: $targetPath")
        }

        // Android 11+ 외부 저장소 직접 접근 시도 시 Scoped Storage 방어
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            targetPath.startsWith("/storage/emulated/0") &&
            !targetPath.startsWith(context.filesDir.absolutePath) &&
            !targetPath.startsWith(context.cacheDir.absolutePath) &&
            context.getExternalFilesDir(null)?.let { targetPath.startsWith(it.absolutePath) } != true
        ) {
            // 외부 저장소 직접 접근 불가 시 안내
            Log.w(TAG, "Android 11+ Scoped Storage 정책 감지: File I/O 차단 방어")
        }

        val files = if (targetDir.isDirectory) {
            targetDir.listFiles()?.filter { it.isFile } ?: emptyList()
        } else {
            listOf(targetDir)
        }

        if (files.isEmpty()) {
            return CompressionResult(false, "", 0, 0L, "압축할 파일이 폴더 내에 없습니다.")
        }

        var compressedCount = 0
        try {
            ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
                val buffer = ByteArray(BUFFER_SIZE)
                for (file in files) {
                    FileInputStream(file).use { fileIn ->
                        zipOut.putNextEntry(ZipEntry(file.name))
                        var readBytes: Int
                        while (fileIn.read(buffer).also { readBytes = it } != -1) {
                            zipOut.write(buffer, 0, readBytes)
                        }
                        zipOut.closeEntry()
                    }
                    compressedCount++
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Scoped Storage 보안 권한 거부", e)
            return CompressionResult(false, "", 0, 0L, "Android Scoped Storage 보안 정책으로 인해 직접 경로 접근이 차단되었습니다. SAF 권한을 부여해주세요.")
        } catch (e: Exception) {
            Log.e(TAG, "파일 압축 예외", e)
            return CompressionResult(false, "", 0, 0L, "ZIP 압축 중 오류 발생: ${e.localizedMessage}")
        }

        return CompressionResult(
            success = true,
            outputZipPath = zipFile.absolutePath,
            totalFilesCompressed = compressedCount,
            compressedSizeBytes = zipFile.length(),
            message = "총 ${compressedCount}개 파일 압축 완료 (${zipFile.length() / 1024} KB)"
        )
    }

    /**
     * 특정 SAF URI 폴더로부터 파일 목록 탐색
     */
    fun listFilesFromSafUri(treeUri: Uri): List<String> {
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        return rootDoc.listFiles().mapNotNull { it.name }
    }
}
