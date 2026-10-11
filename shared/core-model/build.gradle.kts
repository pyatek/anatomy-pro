plugins { id("anatomypro.kmp.library") }

kotlin {
    // The server shares these types (backend spec §3.1).
    jvm()
    android { namespace = "com.ptk.anatomypro.core.model" }

    sourceSets {
        commonTest.dependencies { implementation(libs.kotlin.test) }
    }
}
