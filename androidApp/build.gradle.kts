import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

/**
 * Stages the pipeline's Phase 0 pack into the APK when one has been generated.
 *
 * The pack is 22 MB and `pipeline/build` is not committed, so this is deliberately
 * optional: with no pipeline output the copy produces nothing and the harness falls back
 * to its built-in toy asset. Bundling rather than pushing over adb is what lets the
 * §6.1 measurement be taken on a phone from a plain install.
 */
abstract class StagePhase0Pack : DefaultTask() {

    // A file collection rather than an input directory: the pipeline may never have been
    // run, and a missing directory would fail the build instead of being skipped.
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val pack: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun stage() {
        val assets = File(outputDirectory.get().asFile, "packs")
        assets.deleteRecursively()
        assets.mkdirs()
        val source = pack.files.firstOrNull { it.isFile } ?: return
        source.copyTo(File(assets, "trunk-all-systems.glb"), overwrite = true)
        logger.lifecycle("Bundled Phase 0 pack: ${source.length() / 1024 / 1024} MB")
    }
}

val stagePhase0Pack by tasks.registering(StagePhase0Pack::class) {
    description = "Copies the generated Phase 0 content pack into the app's assets."
    pack.from(rootProject.layout.projectDirectory.file("pipeline/build/packs/trunk-all-systems/mesh.glb"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(
            stagePhase0Pack,
            StagePhase0Pack::outputDirectory,
        )
    }
}

android {
    namespace = "com.ptk.anatomypro"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.ptk.anatomypro"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}