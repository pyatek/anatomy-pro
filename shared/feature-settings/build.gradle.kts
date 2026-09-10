plugins { id("anatomypro.kmp.compose") }

kotlin {
    android { namespace = "com.ptk.anatomypro.feature.settings" }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core-data"))
            api(project(":shared:renderer-api"))
            implementation(project(":shared:core-designsystem"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
