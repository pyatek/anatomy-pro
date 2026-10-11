plugins {
    id("anatomypro.kmp.library")
    alias(libs.plugins.kotlinSerialization)
}

// What the app and the server say to each other (backend spec §5 to §8), defined once and
// compiled into both ends.
kotlin {
    jvm()
    android { namespace = "com.ptk.anatomypro.api" }

    sourceSets {
        commonMain.dependencies { api(libs.kotlinx.serialization.json) }
        commonTest.dependencies { implementation(libs.kotlin.test) }
    }
}
