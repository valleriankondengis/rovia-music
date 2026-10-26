package com.rovia.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.rovia.music.ui.permissions.RequestAudioPermissionOnFirstLaunch
import com.rovia.music.ui.theme.RoviaTheme

class MainActivity : ComponentActivity() {

    private lateinit var appContainer: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.enableEdgeToEdge(window)
        window.isNavigationBarContrastEnforced = false

        appContainer = AppContainer(applicationContext)

        setContent {
            RoviaTheme {
                RequestAudioPermissionOnFirstLaunch()
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Navigation(
                        appContainer = appContainer
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        appContainer.playbackController.connect()
    }

    override fun onStop() {
        appContainer.playbackController.disconnect()
        super.onStop()
    }
}