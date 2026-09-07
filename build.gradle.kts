// Top-level Build-Datei. Plugin-Versionen zentral, angewandt in :app.
plugins {
    // AGP 9.4.0: Gradle >= 9.6.0, JDK 17, max API 37
    // https://developer.android.com/build/releases/agp-9-4-0-release-notes
    id("com.android.application") version "9.4.0" apply false
    // Kotlin kommt seit AGP 9.0 built-in (KGP 2.2.10 laut AGP-POM) — KEIN org.jetbrains.kotlin.android mehr.
    // Compose-Compiler-Plugin: Version = Kotlin-Version des Builds.
    // https://developer.android.com/develop/ui/compose/compiler
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}
