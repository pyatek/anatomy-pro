plugins {
    id("anatomypro.kmp.library")
    alias(libs.plugins.kotlinSerialization)
}

// The quiz generator, the types it speaks in, and the contracts of what the server stands
// behind (the daily, the boards, entitlements), apart from the database: the server
// generates the daily set with this same code (backend spec §3.1), and the client that
// talks to it is tested against it on the JVM, so this also builds for the JVM. The packages are the ones these files had inside core-data, which re-exports
// this module; nothing that imports them had to change.
kotlin {
    jvm()
    android { namespace = "com.ptk.anatomypro.core.quiz" }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core-model"))
            api(libs.kotlinx.datetime)
            api(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(project(":shared:core-quiz-fixtures"))
        }
    }
}
