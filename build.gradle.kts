buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // Newer Kotlin than the one bundled with AGP: GeckoView/AndroidX ship stdlib 2.4.x.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
        classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.4.20")
    }
}

plugins {
    id("com.android.application") version "9.4.1" apply false
}
