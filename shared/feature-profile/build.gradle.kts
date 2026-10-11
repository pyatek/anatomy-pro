plugins { id("anatomypro.kmp.compose") }

kotlin {
    android { namespace = "com.ptk.anatomypro.feature.profile" }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.components.resources)
            api(project(":shared:core-data"))
            implementation(project(":shared:core-designsystem"))
            // For systemNames: the system names are written down once, in feature-atlas.
            implementation(project(":shared:feature-atlas"))
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
