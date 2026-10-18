package com.yggdrasil.assistant.nlp

import java.lang.StringBuilder

/**
 * [KoreanKeyboardAutomata]
 * 1. QWERTY 영문 키보드 오타 -> 두벌식 한글 실시간 복원 오토마타.
 *    (예: "zkzhxhrodptj skdprp dkssuddlfkrh answk qhsoawn" -> "카톡에서 나에게 안녕이라고 문자 보내줘")
 * 2. 영문 외래어/앱 명칭 한국어 표기 매핑.
 *    (예: "kakaotalk" -> "카카오톡", "youtube" -> "유튜브", "message" -> "메시지")
 */
object KoreanKeyboardAutomata {

    // 초성 (19자)
    private val CHOSUNG = charArrayOf(
        'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ',
        'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
    )

    // 중성 (21자)
    private val JUNGSUNG = charArrayOf(
        'ㅏ', 'ㅐ', 'ㅑ', 'ㅒ', 'ㅓ', 'ㅔ', 'ㅕ', 'ㅖ', 'ㅗ', 'ㅘ',
        'ㅙ', 'ㅚ', 'ㅛ', 'ㅜ', 'ㅝ', 'ㅞ', 'ㅟ', 'ㅠ', 'ㅡ', 'ㅢ', 'ㅣ'
    )

    // 종성 (28자, 인덱스 0은 종성 없음)
    private val JONGSUNG = charArrayOf(
        ' ', 'ㄱ', 'ㄲ', 'ㄳ', 'ㄴ', 'ㄵ', 'ㄶ', 'ㄷ', 'ㄹ', 'ㄺ',
        'ㄻ', 'ㄼ', 'ㄽ', 'ㄾ', 'ㄿ', 'ㅀ', 'ㅁ', 'ㅂ', 'ㅄ', 'ㅅ',
        'ㅆ', 'ㅇ', 'ㅈ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
    )

    // 영문 키 -> 초성 인덱스
    private val ENG_TO_CHO = mapOf(
        'r' to 0, 'R' to 1, 's' to 2, 'e' to 3, 'E' to 4,
        'f' to 5, 'a' to 6, 'q' to 7, 'Q' to 8, 't' to 9,
        'T' to 10, 'd' to 11, 'w' to 12, 'W' to 13, 'c' to 14,
        'z' to 15, 'x' to 16, 'v' to 17, 'g' to 18
    )

    // 영문 키 -> 중성 인덱스
    private val ENG_TO_JUNG = mapOf(
        'k' to 0, 'o' to 1, 'i' to 2, 'O' to 3, 'j' to 4,
        'p' to 5, 'u' to 6, 'P' to 7, 'h' to 8, 'y' to 12,
        'n' to 13, 'b' to 17, 'm' to 18, 'l' to 20
    )

    // 영문 키 -> 종성 인덱스
    private val ENG_TO_JONG = mapOf(
        'r' to 1, 'R' to 2, 's' to 4, 'e' to 7, 'f' to 8,
        'a' to 16, 'q' to 17, 't' to 19, 'T' to 20, 'd' to 21,
        'w' to 22, 'c' to 23, 'z' to 24, 'x' to 25, 'v' to 26, 'g' to 27
    )

    // 중성 복합 모음 결합: (기존 중성, 추가 영문키) -> 합성 중성 인덱스
    private val JUNG_COMBINE = mapOf(
        Pair(8, 'k') to 9,   // ㅗ + ㅏ = ㅘ
        Pair(8, 'o') to 10,  // ㅗ + ㅐ = ㅙ
        Pair(8, 'l') to 11,  // ㅗ + ㅣ = ㅚ
        Pair(13, 'j') to 14, // ㅜ + ㅓ = ㅝ
        Pair(13, 'p') to 15, // ㅜ + ㅔ = ㅞ
        Pair(13, 'l') to 16, // ㅜ + ㅣ = ㅟ
        Pair(18, 'l') to 19  // ㅡ + ㅣ = ㅢ
    )

    // 종성 복합 자음 결합: (기존 종성, 추가 영문키) -> 합성 종성 인덱스
    private val JONG_COMBINE = mapOf(
        Pair(1, 't') to 3,   // ㄱ + ㅅ = ㄳ
        Pair(4, 'w') to 5,   // ㄴ + ㅈ = ㄵ
        Pair(4, 'g') to 6,   // ㄴ + ㅎ = ㄶ
        Pair(8, 'r') to 9,   // ㄹ + ㄱ = ㄺ
        Pair(8, 'a') to 10,  // ㄹ + ㅁ = ㄻ
        Pair(8, 'q') to 11,  // ㄹ + ㅂ = ㄼ
        Pair(8, 't') to 12,  // ㄹ + ㅅ = ㄽ
        Pair(8, 'x') to 13,  // ㄹ + ㅌ = ㄾ
        Pair(8, 'v') to 14,  // ㄹ + ㅍ = ㄿ
        Pair(8, 'g') to 15,  // ㄹ + ㅎ = ㅀ
        Pair(17, 't') to 18  // ㅂ + ㅅ = ㅄ
    )

    // 복합 종성이 모음과 만났을 때 분해: 합성종성 -> (남을 종성, 다음 초성)
    private val JONG_SPLIT_MAP = mapOf(
        3 to Pair(1, 9),    // ㄳ -> ㄱ, ㅅ
        5 to Pair(4, 12),   // ㄵ -> ㄴ, ㅈ
        6 to Pair(4, 18),   // ㄶ -> ㄴ, ㅎ
        9 to Pair(8, 0),    // ㄺ -> ㄹ, ㄱ
        10 to Pair(8, 6),   // ㄻ -> ㄹ, ㅁ
        11 to Pair(8, 7),   // ㄼ -> ㄹ, ㅂ
        12 to Pair(8, 9),   // ㄽ -> ㄹ, ㅅ
        13 to Pair(8, 16),  // ㄾ -> ㄹ, ㅌ
        14 to Pair(8, 17),  // ㄿ -> ㄹ, ㅍ
        15 to Pair(8, 18),  // ㅀ -> ㄹ, ㅎ
        18 to Pair(17, 9)   // ㅄ -> ㅂ, ㅅ
    )

    // 단일 종성 -> 초성 인덱스 변환
    private val JONG_TO_CHO = mapOf(
        1 to 0, 2 to 1, 4 to 2, 7 to 3, 8 to 5,
        16 to 6, 17 to 7, 19 to 9, 20 to 10, 21 to 11,
        22 to 12, 23 to 14, 24 to 15, 25 to 16, 26 to 17, 27 to 18
    )

    // 주요 영문 단어 및 외래어 사전 (발음 및 앱 한글화)
    private val ENGLISH_LOANWORDS = mapOf(
        "kakao" to "카카오",
        "kakaotalk" to "카카오톡",
        "katalk" to "카톡",
        "youtube" to "유튜브",
        "yt" to "유튜브",
        "insta" to "인스타",
        "instagram" to "인스타그램",
        "message" to "메시지",
        "messages" to "메시지",
        "sms" to "문자",
        "telegram" to "텔레그램",
        "line" to "라인",
        "chrome" to "크롬",
        "google" to "구글",
        "naver" to "네이버",
        "map" to "지도",
        "maps" to "지도",
        "tmap" to "티맵",
        "wifi" to "와이파이",
        "bluetooth" to "블루투스",
        "camera" to "카메라",
        "gallery" to "갤러리",
        "photo" to "사진",
        "photos" to "사진",
        "music" to "음악",
        "alarm" to "알람",
        "clock" to "시계",
        "setting" to "설정",
        "settings" to "설정",
        "capture" to "화면 캡처",
        "screenshot" to "스크린샷",
        "call" to "전화",
        "phone" to "전화",
        "contact" to "연락처",
        "contacts" to "연락처",
        "calendar" to "캘린더",
        "memo" to "메모",
        "notes" to "메모",
        "calculator" to "계산기",
        "flashlight" to "손전등",
        "flash" to "손전등",
        "brightness" to "밝기",
        "volume" to "음량",
        "battery" to "배터리"
    )

    /**
     * 문장 내에 섞인 영문 키보드 오타나 영문 외래어를 한국어로 완전 자동 복원
     */
    fun convertEnglishToKorean(text: String): String {
        if (text.isBlank()) return text

        val tokens = text.split(" ")
        val sb = StringBuilder()

        for (i in tokens.indices) {
            val token = tokens[i]
            if (token.isBlank()) continue

            // 1. 단어 뒤 조사 분리 (예: "kakaotalk-eseo", "kakaotalk에서")
            val convertedToken = resolveToken(token)
            sb.append(convertedToken)
            if (i < tokens.size - 1) {
                sb.append(" ")
            }
        }

        return sb.toString()
    }

    private fun resolveToken(token: String): String {
        val lower = token.lowercase()

        // 1. 외래어/앱 명칭 사전에 직접 매칭되는 경우
        ENGLISH_LOANWORDS[lower]?.let { return it }

        // 2. 외래어 + 한글 조사 결합 형태 (예: "youtube로", "kakao에서")
        for ((eng, kor) in ENGLISH_LOANWORDS) {
            if (lower.startsWith(eng) && lower.length > eng.length) {
                val suffix = token.substring(eng.length)
                // 만약 접미사가 한글이면 외래어 치환 후 결합
                if (suffix.all { it in '\uAC00'..'\uD7A3' || it in '\u3131'..'\u318E' }) {
                    return kor + suffix
                }
            }
        }

        // 3. 순수 영문(알파벳)으로만 이루어졌다면 두벌식 오토마타 변환 시도
        val isAlphaOnly = token.all { (it in 'a'..'z') || (it in 'A'..'Z') }
        if (isAlphaOnly) {
            val dubeolsik = engToKorDubeolsik(token)
            // 변환된 결과가 의미 있는 한글을 포함하면 채택
            if (dubeolsik.any { it in '\uAC00'..'\uD7A3' }) {
                return dubeolsik
            }
        }

        return token
    }

    /**
     * 두벌식 키보드 오토마타 (영타 -> 한글)
     */
    fun engToKorDubeolsik(eng: String): String {
        val result = StringBuilder()
        var cho = -1
        var jung = -1
        var jong = -1

        fun flushSyllable() {
            if (cho != -1 && jung != -1) {
                val jongIdx = if (jong != -1) jong else 0
                val unicode = 0xAC00 + (cho * 21 + jung) * 28 + jongIdx
                result.append(unicode.toChar())
            } else if (cho != -1) {
                result.append(CHOSUNG[cho])
            } else if (jung != -1) {
                result.append(JUNGSUNG[jung])
            }
            cho = -1
            jung = -1
            jong = -1
        }

        var i = 0
        while (i < eng.length) {
            val c = eng[i]

            val isChoKey = ENG_TO_CHO.containsKey(c)
            val isJungKey = ENG_TO_JUNG.containsKey(c)

            if (isJungKey) {
                // 모음 입력 시점
                val jungVal = ENG_TO_JUNG[c]!!

                if (cho == -1) {
                    // 초성이 없는 상태에서 모음 단독 입력
                    flushSyllable()
                    jung = jungVal
                } else if (jung == -1) {
                    // 초성 있고 모음 처음 들어옴 -> 음절 완성 진행
                    jung = jungVal
                } else if (jong == -1) {
                    // 이미 중성이 있는데 또 모음이 들어옴 -> 복합 모음 결합 시도 (예: ㅗ + ㅏ = ㅘ)
                    val combined = JUNG_COMBINE[Pair(jung, c)]
                    if (combined != null) {
                        jung = combined
                    } else {
                        // 복합 모음 안 되면 이전 음절 flush 후 모음 단독
                        flushSyllable()
                        jung = jungVal
                    }
                } else {
                    // 이미 종성까지 있는 상태에서 모음이 들어옴 -> 연음 처리 (종성이 다음 초성으로 이동)
                    val split = JONG_SPLIT_MAP[jong]
                    if (split != null) {
                        // 복합 종성이면 앞 종성 남기고 뒷 종성이 다음 초성으로
                        jong = split.first
                        flushSyllable()
                        cho = split.second
                        jung = jungVal
                    } else {
                        // 단일 종성이면 통째로 다음 초성으로
                        val nextCho = JONG_TO_CHO[jong] ?: 0
                        jong = -1
                        flushSyllable()
                        cho = nextCho
                        jung = jungVal
                    }
                }
            } else if (isChoKey) {
                // 자음 입력 시점
                val choVal = ENG_TO_CHO[c]!!

                if (cho == -1) {
                    // 음절 시작
                    flushSyllable()
                    cho = choVal
                } else if (jung == -1) {
                    // 초성만 있는데 또 자음이 들어옴 -> 이전 자음 flush 후 새 초성
                    flushSyllable()
                    cho = choVal
                } else if (jong == -1) {
                    // 초성+중성 완성 상태에서 첫 종성 후보 자음 진입
                    val jongVal = ENG_TO_JONG[c]
                    if (jongVal != null) {
                        // 다음 글자가 모음인지 lookahead
                        val nextIsJung = (i + 1 < eng.length) && ENG_TO_JUNG.containsKey(eng[i + 1])
                        if (nextIsJung) {
                            // 다음 글자가 모음이면 종성으로 받지 않고 바로 flush 후 새 초성으로 넘김
                            flushSyllable()
                            cho = choVal
                        } else {
                            jong = jongVal
                        }
                    } else {
                        flushSyllable()
                        cho = choVal
                    }
                } else {
                    // 이미 종성이 있는 상태에서 또 자음 진입 -> 복합 종성 결합 시도 (예: ㄱ + ㅅ = ㄳ)
                    val combinedJong = JONG_COMBINE[Pair(jong, c)]
                    val nextIsJung = (i + 1 < eng.length) && ENG_TO_JUNG.containsKey(eng[i + 1])

                    if (combinedJong != null && !nextIsJung) {
                        jong = combinedJong
                    } else {
                        // 결합 불가하거나 다음이 모음이면 flush 후 새 초성
                        flushSyllable()
                        cho = choVal
                    }
                }
            } else {
                // 특수문자나 숫자 등
                flushSyllable()
                result.append(c)
            }
            i++
        }

        flushSyllable()
        return result.toString()
    }
}
