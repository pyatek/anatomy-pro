plugins {
    id("anatomypro.kmp.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    android { namespace = "com.ptk.anatomypro.core.data" }

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
