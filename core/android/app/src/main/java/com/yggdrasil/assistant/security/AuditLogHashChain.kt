package com.yggdrasil.assistant.security

import com.yggdrasil.assistant.rag.MuninSqlCipherDatabase
import java.security.MessageDigest

/**
 * AuditLogHashChain
 * 위변조 불가 암호학적 해시 체인(Cryptographic Hash Chain) 기반 실시간 감사 로그 엔진
 * 
 * 1. AI가 수행한 모든 조작(화면 터치, 파일 압축/전송, 일정 등록, 기기 제어)을 해시 체인으로 봉인
 * 2. 각 로그 블록은 직전 블록의 해시(previousHash)를 포함하여 SHA-256으로 링크
 * 3. 프로세스 킬 시에도 소실되지 않도록 MuninSqlCipherDatabase 암호화 테이블에 영구 영속화
 */
class AuditLogHashChain(
    private val database: MuninSqlCipherDatabase? = null
) {

    data class AuditBlock(
        val index: Long,
        val timestamp: String,
        val actionType: String,
        val securityLevel: String,
        val realm: String,
        val details: String,
        val previousHash: String,
        val hash: String,
        val status: String // SUCCESS, BLOCKED, BREAK_GLASS, MULTI_SIG
    )

    private val chain = mutableListOf<AuditBlock>()
    private val GENESIS_HASH = "0000000000000000000000000000000000000000000000000000000000000000"

    init {
        // 1. DB로부터 기존 영속화된 체인 복원 시도
        val restored = database?.loadAllAuditBlocks()
        if (!restored.isNullOrEmpty()) {
            synchronized(chain) {
                for (dbb in restored) {
                    chain.add(
                        AuditBlock(
                            index = dbb.index,
                            timestamp = dbb.timestamp,
                            actionType = dbb.actionType,
                            securityLevel = dbb.securityLevel,
                            realm = dbb.realm,
                            details = dbb.details,
                            previousHash = dbb.previousHash,
                            hash = dbb.hash,
                            status = dbb.status
                        )
                    )
                }
            }
        } else {
            // DB가 비어있는 경우에만 제네시스(최초) 블록 생성 및 영속화
            appendBlock(
                actionType = "SYSTEM_GENESIS",
                securityLevel = "L0",
                realm = "Asgard",
                details = "Yggdrasil 보안 감사 로그 해시 체인이 초기화되었습니다.",
                status = "SUCCESS"
            )
        }
    }

    /**
     * 새로운 감사 로그를 해시 체인에 추가 및 봉인하고 암호화 DB에 영속화
     */
    fun appendBlock(
        actionType: String,
        securityLevel: String,
        realm: String,
        details: String,
        status: String = "SUCCESS"
    ): AuditBlock {
        synchronized(chain) {
            val index = chain.size.toLong()
            val timestamp = java.time.Instant.now().toString()
            val prevHash = if (chain.isEmpty()) GENESIS_HASH else chain.last().hash

            val rawData = "$index|$timestamp|$actionType|$securityLevel|$realm|$details|$prevHash|$status"
            val blockHash = calculateSha256(rawData)

            val block = AuditBlock(
                index = index,
                timestamp = timestamp,
                actionType = actionType,
                securityLevel = securityLevel,
                realm = realm,
                details = details,
                previousHash = prevHash,
                hash = blockHash,
                status = status
            )

            chain.add(block)

            // DB 영속화
            database?.insertAuditBlock(
                MuninSqlCipherDatabase.DbAuditBlock(
                    index = block.index,
                    timestamp = block.timestamp,
                    actionType = block.actionType,
                    securityLevel = block.securityLevel,
                    realm = block.realm,
                    details = block.details,
                    previousHash = block.previousHash,
                    hash = block.hash,
                    status = block.status
                )
            )

            return block
        }
    }

    /**
     * 전체 해시 체인의 위변조 여부 검증
     * @return true이면 무결함, false이면 중간 변조 감지
     */
    fun verifyChainIntegrity(): Boolean {
        synchronized(chain) {
            for (i in chain.indices) {
                val current = chain[i]

                // 1. 이전 블록 해시 연결 검증
                if (i == 0) {
                    if (current.previousHash != GENESIS_HASH) return false
                } else {
                    val prev = chain[i - 1]
                    if (current.previousHash != prev.hash) return false
                }

                // 2. 현재 블록 자체 해시 무결성 재계산 검증
                val expectedRaw = "${current.index}|${current.timestamp}|${current.actionType}|${current.securityLevel}|${current.realm}|${current.details}|${current.previousHash}|${current.status}"
                val expectedHash = calculateSha256(expectedRaw)
                if (current.hash != expectedHash) return false
            }
            return true
        }
    }

    /**
     * 최신 감사 로그 리스트 조회 (UI 및 대시보드 출력용)
     */
    fun getRecentBlocks(limit: Int = 20): List<AuditBlock> {
        synchronized(chain) {
            return chain.takeLast(limit).reversed()
        }
    }

    private fun calculateSha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
