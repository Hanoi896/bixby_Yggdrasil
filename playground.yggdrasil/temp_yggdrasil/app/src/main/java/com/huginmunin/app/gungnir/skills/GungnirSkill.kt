package com.huginmunin.app.gungnir.skills

/**
 * Gungnir Skill Interface
 * Gungnir가 사용할 수 있는 능력의 기본 형태입니다.
 */
interface GungnirSkill {
    val name: String
    suspend fun execute(params: Map<String, Any>): Boolean
}
