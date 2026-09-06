plugins { id("anatomypro.kmp.library") }

kotlin {
    android { namespace = "com.ptk.anatomypro.renderer.filament" }

    sourceSets {
        commonMain.dependencies { api(project(":shared:renderer-api")) }
        commonTest.dependencies { implementation(libs.kotlin.test) }
    }
}
