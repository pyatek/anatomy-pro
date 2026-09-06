plugins { id("anatomypro.kmp.library") }

kotlin {
    android { namespace = "com.ptk.anatomypro.core.model" }

    sourceSets {
        commonTest.dependencies { implementation(libs.kotlin.test) }
    }
}
