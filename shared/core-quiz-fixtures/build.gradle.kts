plugins { id("anatomypro.kmp.library") }

// A small atlas with every case the generator has to handle, for the tests of every module
// that generates from it: the generator's own, the app's repository, and the server's.
kotlin {
    jvm()
    android { namespace = "com.ptk.anatomypro.core.quiz.fixtures" }

    sourceSets {
        commonMain.dependencies { api(project(":shared:core-quiz")) }
    }
}
