plugins { id("anatomypro.kmp.library") }

kotlin {
    android { namespace = "com.ptk.anatomypro.core.data.fake" }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core-data"))
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
