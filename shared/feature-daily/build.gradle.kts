plugins { id("anatomypro.kmp.compose") }

kotlin {
    android { namespace = "com.ptk.anatomypro.feature.daily" }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.components.resources)
            api(project(":shared:core-data"))
            // The daily is played on the quiz's question screen, as a QuizStage.Asking.
            api(project(":shared:feature-quiz"))
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
            implementation(project(":shared:core-data-fake"))
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
