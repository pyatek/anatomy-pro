import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins { `kotlin-dsl` }

// Pin both compilers to 21 so they agree. Without this, Java targets the daemon's JDK
// (25) while Kotlin silently falls back to 24 — Gradle reports that mismatch as a
// problem. Explicit targets fix it on any JDK 21 or newer, whereas a toolchain would
// demand a specific local JDK, and this build configures no toolchain resolver.
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions { jvmTarget = JvmTarget.JVM_21 }
}

dependencies {
    implementation(libs.androidKmpLibrary.gradlePlugin)
    implementation(libs.kotlinMultiplatform.gradlePlugin)
    implementation(libs.composeMultiplatform.gradlePlugin)
    implementation(libs.composeCompiler.gradlePlugin)
}
