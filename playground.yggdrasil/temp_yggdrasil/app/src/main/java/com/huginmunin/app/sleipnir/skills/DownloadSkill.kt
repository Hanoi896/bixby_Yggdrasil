package com.huginmunin.app.sleipnir.skills

import com.huginmunin.app.munin.MuninRepository
import kotlinx.coroutines.delay
import javax.inject.Inject

/**
 * Download Skill
 * 파일 다운로드 등 고속/병렬 작업을 수행합니다.
 */
class DownloadSkill @Inject constructor(
    private val munin: MuninRepository
) {
    suspend fun downloadFile(url: String) {
        munin.log("Sleipnir", "Downloading from $url", "INFO")
        
        // Simulate network delay
        delay(500)
        
        munin.log("Sleipnir", "Download complete: $url", "INFO")
    }
}
