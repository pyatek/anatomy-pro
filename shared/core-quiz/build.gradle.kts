plugins {
    id("anatomypro.kmp.library")
    alias(libs.plugins.kotlinSerialization)
}

// The quiz generator and the types it speaks in, apart from the database: the server
// generates the daily set with this same code (backend spec §3.1), so it also builds for
// the JVM. The packages are the ones these files had inside core-data, which re-exports
// this module; nothing that imports them had to change.
kotlin {
    jvm()
    android { namespace = "com.ptk.anatomypro.core.quiz" }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core-model"))
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(project(":shared:core-quiz-fixtures"))
        }
    }
}
