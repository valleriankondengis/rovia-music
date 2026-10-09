
package com.rovia.music

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    override fun onResume() {
        super.onResume()

        /*
         * Recheck permission whenever the app becomes active.
         *
         * If permission is available, request catalog synchronization
         * through the application-scoped coordinator. This does not
         * block Activity startup or the Compose UI.
         */
        if (hasAudioPermission()) {
            (application as RoviaApplication)
                .appContainer
                .synchronizeMusicCatalog()
        }
    }

    override fun onCreate(
        savedInstanceState: Bundle?,
    ) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        val roviaApplication =
            application as RoviaApplication

        setContent {
            com.rovia.music.theme.RoviaTheme {
                Surface(
                    modifier =
                        Modifier.fillMaxSize(),
                    color =
                        MaterialTheme
                            .colorScheme
                            .background,
                ) {
                    var hasAudioPermission by
                        remember {
                            mutableStateOf(
                                hasAudioPermission(),
                            )
                        }

                    val permissionLauncher =
                        rememberLauncherForActivityResult(
                            contract =
                                ActivityResultContracts.RequestPermission(),
                        ) { granted ->
                            hasAudioPermission = granted

                            /*
                             * Permission may have been granted while
                             * the Activity was paused. Start sync here
                             * as well as in onResume so either lifecycle
                             * ordering is handled safely.
                             */
                            if (granted) {
                                roviaApplication
                                    .appContainer
                                    .synchronizeMusicCatalog()
                            }
                        }

                    LaunchedEffect(
                        hasAudioPermission,
                    ) {
                        if (!hasAudioPermission) {
                            permissionLauncher.launch(
                                Manifest.permission.READ_MEDIA_AUDIO,
                            )
                        }
                    }

                    if (hasAudioPermission) {
                        MainNavigation(
                            musicRepository =
                                roviaApplication
                                    .appContainer
                                    .musicRepository,
                            folderBrowserRepository =
                                roviaApplication
                                    .appContainer
                                    .folderBrowserRepository,
                            folderFilterRepository =
                                roviaApplication
                                    .appContainer
                                    .folderFilterRepository,
                            folderScannerRepository =
                                roviaApplication
                                    .appContainer
                                    .folderScannerRepository,
                            playbackController =
                                roviaApplication
                                    .appContainer
                                    .playbackController,
                            lyricsRepository =
                                roviaApplication
                                    .appContainer
                                    .lyricsRepository,
                        )
                    } else {
                        Column(
                            modifier =
                                Modifier.fillMaxSize(),
                            horizontalAlignment =
                                Alignment.CenterHorizontally,
                            verticalArrangement =
                                Arrangement.Center,
                        ) {
                            Text(
                                stringResource(
                                    R.string.permission_music_message,
                                ),
                            )

                            Button(
                                onClick = {
                                    permissionLauncher.launch(
                                        Manifest.permission.READ_MEDIA_AUDIO,
                                    )
                                },
                            ) {
                                Text(
                                    stringResource(
                                        R.string.permission_allow_music,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_MEDIA_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
    }
}
