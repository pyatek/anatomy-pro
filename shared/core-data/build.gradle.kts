plugins {
    id("anatomypro.kmp.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    android {
        namespace = "com.ptk.anatomypro.core.data"
        // Room's Android builder needs a Context and the bundled SQLite driver needs a
        // real runtime, so the Android half of the database is proven on a device.
        withDeviceTestBuilder { sourceSetTreeName = "test" }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core-model"))
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.androidx.testExt.junit)
            implementation(libs.androidx.test.runner)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

// Schemas are exported so a migration is a reviewable diff rather than something
// discovered at runtime on a device that already has data on it.
room { schemaDirectory("$projectDir/schemas") }

dependencies {
    listOf("kspAndroid", "kspIosArm64", "kspIosSimulatorArm64").forEach {
        add(it, libs.room.compiler)
    }
}

// Lint reads KSP's generated sources without declaring that it does, which Gradle rejects
// as an undeclared dependency. Ordering is all that is actually missing.
tasks.matching { it.name.startsWith("lintAnalyze") || it.name.endsWith("LintModel") }
    .configureEach { mustRunAfter(tasks.matching { task -> task.name.startsWith("ksp") }) }
