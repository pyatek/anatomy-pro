plugins {
    id("anatomypro.kmp.compose")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    android { namespace = "com.ptk.anatomypro.navigation" }

    sourceSets {
        commonMain.dependencies {
            api(libs.navigation.compose)
            implementation(libs.compose.runtime)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
