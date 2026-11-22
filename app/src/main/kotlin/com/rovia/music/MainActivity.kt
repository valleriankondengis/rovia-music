package com.rovia.music

import android.content.Intent
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

object ShortcutActions {
    const val PLAY_RECENT = "com.rovia.music.action.PLAY_RECENT"
    const val PLAY_SHUFFLE = "com.rovia.music.action.PLAY_SHUFFLE"
}

class MainActivity : ComponentActivity() {

    private lateinit var appContainer: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.enableEdgeToEdge(window)
        window.isNavigationBarContrastEnforced = false

        appContainer = AppContainer(applicationContext)
        handleShortcutIntent(intent)

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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShortcutIntent(intent)
    }

    private fun handleShortcutIntent(intent: Intent?) {
        when (intent?.action) {
            ShortcutActions.PLAY_RECENT, ShortcutActions.PLAY_SHUFFLE ->
                appContainer.pendingShortcutAction.value = intent.action
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

    override fun onDestroy() {
        if (::appContainer.isInitialized) {
            appContainer.close()
        }
        super.onDestroy()
    }
}