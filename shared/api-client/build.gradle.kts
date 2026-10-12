plugins { id("anatomypro.kmp.library") }

// The app's side of the wire: the repositories for what the server stands behind (backend
// spec §3.1, plan stage 5). It builds for the JVM too, so that the server's own tests can
// drive the real server through these repositories — the test that catches a field renamed
// on one side.
kotlin {
    jvm()
    android { namespace = "com.ptk.anatomypro.api.client" }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared:api-contract"))
            api(project(":shared:core-quiz"))
            api(libs.ktor.client.core)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies { implementation(libs.kotlin.test) }
    }
}
