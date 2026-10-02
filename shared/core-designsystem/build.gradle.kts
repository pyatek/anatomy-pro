plugins { id("anatomypro.kmp.compose") }

kotlin {
    android { namespace = "com.ptk.anatomypro.core.designsystem" }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared:renderer-api"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
        }
        commonTest.dependencies { implementation(libs.kotlin.test) }
    }
}
