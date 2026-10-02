plugins {
    id("anatomypro.kmp.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    android {
        // compose-resources bundles (strings.cvr and friends) are Android resources. The KMP
        // library plugin leaves them off by default, and then the bundle is silently left out
        // of the APK: the module compiles and its host tests pass, and the first
        // stringResource() call crashes at runtime with MissingResourceException.
        androidResources { enable = true }
    }
}
