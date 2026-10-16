package com.yggdrasil.assistant.rag

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * MuninEmbeddingEngine
 * 기억 서기관 Munin 온디바이스 시맨틱 임베딩 & RAG 엔진
 * 
 * 1. 온디바이스 NPU C++ 가속 런타임(yggdrasil_llm)의 llama.cpp Hidden States 기반 실제 의미 텐서 추출
 * 2. 텍스트 간 L2 정규화(Unit Normalization) 기반 초고속 코사인 유사도(Cosine Similarity) 연산
 * 3. 온디바이스 모델 미로드 시에도 가짜 난수(Random)를 배제하고 결정론적 서브워드 바이그램 TF 벡터로 안전하게 폴백
 */
class MuninEmbeddingEngine {

    companion object {
        private const val TAG = "MuninEmbeddingEngine"
        const val DEFAULT_EMBEDDING_DIM = 2048 // 온디바이스 EXAONE 히든 스테이트 기본 차원
        const val FALLBACK_DIM = 512

        init {
            try {
                System.loadLibrary("yggdrasil_llm")
                Log.i(TAG, "온디바이스 LLM/임베딩 네이티브 라이브러리(yggdrasil_llm) 로드 성공")
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "네이티브 라이브러리 로드 대기: JVM/테스트 환경 폴백 활성화")
            }
        }
    }

    /**
     * 입력 텍스트를 온디바이스 L2 정규화 임베딩 벡터로 변환
     * 1순위: llama.cpp NPU 가속 C API를 통한 실제 딥러닝 히든 스테이트 텐서 추출
     * 2순위: 네이티브 미연결 시 결정론적 서브워드 TF 벡터 생성 (가짜 난수 완전 배제)
     * 
     * @param text 사용자 발화 또는 기억 문장
     * @return L2 정규화된 FloatArray 의미 벡터
     */
    fun embedText(text: String): FloatArray {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) {
            return FloatArray(FALLBACK_DIM)
        }

        // 1. 온디바이스 네이티브 C API (llama.cpp) 호출 시도
        try {
            val nativeVector = computeEmbeddingNative(cleanText)
            if (nativeVector != null && nativeVector.isNotEmpty()) {
                return nativeVector
            }
        } catch (e: UnsatisfiedLinkError) {
            // 네이티브 라이브러리 미바인딩 환경
        } catch (e: Exception) {
            Log.e(TAG, "네이티브 임베딩 추출 중 예외 발생: ${e.message}")
        }

        // 2. 가짜 Random 난수를 쓰지 않는 결정론적 서브워드 바이그램 TF 폴백 벡터
        return computeDeterministicSubwordEmbedding(cleanText, FALLBACK_DIM)
    }

    /**
     * 가짜 난수(Fake Random)를 배제하고 한글 음절 및 단어의 통계적 바이그램 빈도를 반영하는
     * 결정론적(Deterministic) 서브워드 임베딩 벡터 생성기 (L2 정규화 완료)
     */
    private fun computeDeterministicSubwordEmbedding(text: String, dim: Int): FloatArray {
        val vector = FloatArray(dim)
        if (text.isEmpty()) return vector

        // 1. 글자 단위 가중치 투영
        for (i in text.indices) {
            val charCode = text[i].code
            val idx = (charCode * 31 + i) % dim
            val positiveIdx = if (idx < 0) idx + dim else idx
            vector[positiveIdx] += 1.0f
        }

        // 2. 바이그램(음절 쌍) 연속성 투영 (의미적 유사 문맥 반영)
        if (text.length >= 2) {
            for (i in 0 until text.length - 1) {
                val biCode = (text[i].code shl 5) xor text[i + 1].code
                val idx = (biCode * 17) % dim
                val positiveIdx = if (idx < 0) idx + dim else idx
                vector[positiveIdx] += 2.0f
            }
        }

        // 3. L2 정규화 (길이 1.0으로 스케일링하여 코사인 유사도를 단순 내적으로 계산 가능)
        var sumSquares = 0f
        for (v in vector) {
            sumSquares += v * v
        }
        val norm = sqrt(sumSquares.toDouble()).toFloat()
        if (norm > 0f) {
            for (i in 0 until dim) {
                vector[i] /= norm
            }
        }

        return vector
    }

    /**
     * 두 임베딩 벡터 간의 코사인 유사도 계산 (-1.0 ~ 1.0)
     * [차원 불일치(2048 vs 512) 방어 가드레일 실장]
     * - 차원이 다를 경우 공통 차원 슬라이스 후 L2 재정규화로 왜곡 원천 차단
     * - 4-way Loop Unrolling (4-way unrolling) 최적화로 ARMv8 파이프라인 캐시 및 브랜치 예측 극대화
     */
    fun calculateCosineSimilarity(vecA: FloatArray, vecB: FloatArray): Float {
        val sizeA = vecA.size
        val sizeB = vecB.size
        if (sizeA == 0 || sizeB == 0) return 0f

        val compareDim = kotlin.math.min(sizeA, sizeB)
        var dotProduct = 0f
        var normSqA = 0f
        var normSqB = 0f
        var i = 0

        // 차원이 일치하는 경우 (가장 빈번한 최적 경로: 단순 내적)
        if (sizeA == sizeB) {
            val limit = compareDim - 3
            while (i < limit) {
                dotProduct += (vecA[i] * vecB[i]) +
                              (vecA[i + 1] * vecB[i + 1]) +
                              (vecA[i + 2] * vecB[i + 2]) +
                              (vecA[i + 3] * vecB[i + 3])
                i += 4
            }
            while (i < compareDim) {
                dotProduct += vecA[i] * vecB[i]
                i++
            }
            return dotProduct.coerceIn(-1.0f, 1.0f)
        }

        // 차원이 불일치하는 경우 (2048 vs 512): 공통 부분 차원에 대한 L2 재정규화 연산
        val limit = compareDim - 3
        while (i < limit) {
            val a0 = vecA[i]; val b0 = vecB[i]
            val a1 = vecA[i + 1]; val b1 = vecB[i + 1]
            val a2 = vecA[i + 2]; val b2 = vecB[i + 2]
            val a3 = vecA[i + 3]; val b3 = vecB[i + 3]

            dotProduct += (a0 * b0) + (a1 * b1) + (a2 * b2) + (a3 * b3)
            normSqA += (a0 * a0) + (a1 * a1) + (a2 * a2) + (a3 * a3)
            normSqB += (b0 * b0) + (b1 * b1) + (b2 * b2) + (b3 * b3)
            i += 4
        }
        while (i < compareDim) {
            val a = vecA[i]; val b = vecB[i]
            dotProduct += a * b
            normSqA += a * a
            normSqB += b * b
            i++
        }

        val denominator = sqrt((normSqA * normSqB).toDouble()).toFloat()
        if (denominator <= 0f) return 0f
        return (dotProduct / denominator).coerceIn(-1.0f, 1.0f)
    }

    /**
     * FloatArray 벡터를 SQLite/SQLCipher 저장을 위한 ByteArray BLOB으로 직렬화
     */
    fun vectorToBlob(vector: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(vector.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (v in vector) {
            buffer.putFloat(v)
        }
        return buffer.array()
    }

    /**
     * ByteArray BLOB을 FloatArray 벡터로 역직렬화 (손상 방어 가드레일 포함)
     */
    fun blobToVector(blob: ByteArray): FloatArray {
        if (blob.isEmpty()) return FloatArray(0)
        val count = blob.size / 4
        val vector = FloatArray(count)
        val buffer = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) {
            vector[i] = buffer.float
        }
        return vector
    }

    // ==========================================
    // C++ JNI 네이티브 선언
    // ==========================================
    private external fun computeEmbeddingNative(text: String): FloatArray?
}
