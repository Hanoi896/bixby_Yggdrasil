package com.huginmunin.app.draupnir

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Draupnir: The Multiplier (Resource Manager)
 * 시스템 자원(스레드, 메모리)을 관리하고 배분합니다.
 */
@Singleton
class Draupnir @Inject constructor() {

    /**
     * Gungnir (중요 실행)를 위한 스레드
     * 실패하면 안 되는 작업을 위해 안정적인 스레드 할당
     */
    val criticalDispatcher: CoroutineDispatcher = Dispatchers.IO

    /**
     * Sleipnir (고속 실행)를 위한 스레드
     * 병렬 처리에 최적화된 스레드 풀 (추후 커스텀 가능)
     */
    val fastDispatcher: CoroutineDispatcher = Dispatchers.Default

    /**
     * Hugin (사고)를 위한 스레드
     * CPU 연산 집중
     */
    val brainDispatcher: CoroutineDispatcher = Dispatchers.Default

    /**
     * Munin (기억)를 위한 스레드
     * I/O 집중, 낮은 우선순위
     */
    val memoryDispatcher: CoroutineDispatcher = Dispatchers.IO

    fun optimizeResources() {
        // TODO: 메모리 정리 및 GC 유도 로직 (필요 시)
    }
}
