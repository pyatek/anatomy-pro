package com.ptk.anatomypro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // appDependencies() is declared once per build type: fakes in debug, the production
        // set in release. The build type, not a runtime flag, decides which one compiles.
        setContent {
            App(appDependencies())
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App(appDependencies())
}