package com.huginmunin.app.sleipnir

import com.huginmunin.app.draupnir.Draupnir
import com.huginmunin.app.munin.MuninRepository
import com.huginmunin.app.sleipnir.skills.DownloadSkill
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sleipnir: The 8-Legged Horse (Fast Executor)
 * 고속 병렬 처리 및 네트워크 통신을 담당합니다.
 */
@Singleton
class SleipnirRunner @Inject constructor(
    private val draupnir: Draupnir,
    private val munin: MuninRepository,
    private val downloadSkill: DownloadSkill
) {
    private val scope = CoroutineScope(SupervisorJob() + draupnir.fastDispatcher)

    fun download(url: String) {
        scope.launch {
            munin.log("Sleipnir", "Running fast: Download $url")
            downloadSkill.downloadFile(url)
        }
    }
}
