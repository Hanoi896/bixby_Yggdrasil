// 루트 빌드 스크립트: 플러그인 버전만 한곳에서 선언하고 각 모듈에서 적용
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    // Kotlin 2.0부터 Compose 컴파일러는 Kotlin 플러그인으로 제공됨
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
