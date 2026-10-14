package com.yggdrasil.assistant.integration

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * FileManagerSAF
 * Storage Access Framework(SAF) 기반 파일 관리 및 무손실 ZIP 압축 모듈
 * 
 * 1. 다운로드, 문서, 사진 폴더 내 파일 검색 및 SAF URI 접근
 * 2. 100% 무료/순정 자바 IO 기반 대용량 파일 고속 ZIP 압축
 * 3. Hugin compress_files 도구 명령과 1:1 파이프라인 연동
 */
class FileManagerSAF(private val context: Context) {

    data class CompressionResult(
        val success: Boolean,
        val outputZipPath: String,
        val totalFilesCompressed: Int,
        val compressedSizeBytes: Long,
        val message: String
    )

    /**
     * 특정 디렉터리 내의 파일 목록을 ZIP 파일로 압축
     * @param targetDirectoryPath 압축할 대상 폴더 경로
     * @param outputZipName 생성할 ZIP 파일명
     */
    fun compressFiles(
        targetDirectoryPath: String,
        outputZipName: String = "Yggdrasil_Archive.zip"
    ): CompressionResult {
        val targetDir = File(targetDirectoryPath)
        if (!targetDir.exists() || !targetDir.isDirectory) {
            return CompressionResult(false, "", 0, 0L, "대상 디렉터리가 존재하지 않거나 폴더가 아닙니다: $targetDirectoryPath")
        }

        val files = targetDir.listFiles()?.filter { it.isFile } ?: emptyList()
        if (files.isEmpty()) {
            return CompressionResult(false, "", 0, 0L, "압축할 파일이 폴더 내에 없습니다.")
        }

        // 압축 파일 저장 경로 (SAF 캐시/문서 디렉터리)
        val outputDir = File(context.filesDir, "archives").apply { mkdirs() }
        val zipFile = File(outputDir, outputZipName)

        var compressedCount = 0
        try {
            ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
                val buffer = ByteArray(8192)
                for (file in files) {
                    FileInputStream(file).use { fileIn ->
                        val entry = ZipEntry(file.name)
                        zipOut.putNextEntry(entry)
                        var readBytes: Int
                        while (fileIn.read(buffer).also { readBytes = it } != -1) {
                            zipOut.write(buffer, 0, readBytes)
                        }
                        zipOut.closeEntry()
                    }
                    compressedCount++
                }
            }
        } catch (e: Exception) {
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
