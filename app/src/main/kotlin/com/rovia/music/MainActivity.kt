
package com.rovia.music

import android.Manifest
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
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
import androidx.lifecycle.lifecycleScope
import com.rovia.music.core.model.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /**
     * Compose observes these states to determine whether the
     * Activity should render the regular application or the
     * existing full Player surface for an external audio URI.
     */
    private val externalLaunchState =
        mutableStateOf(false)

    private val externalTrackState =
        mutableStateOf<Track?>(null)

    private var isHandlingExternalIntent = false

    private var externalResolveJob: Job? = null

    private var externalSessionStarted = false

    private var externalCloseHandled = false

    override fun onResume() {
        super.onResume()

        /*
         * External playback must not initialize AppContainer,
         * synchronize the catalog, or evaluate Folder Filter
         * as part of opening the selected file.
         */
        if (
            !isHandlingExternalIntent &&
            hasAudioPermission()
        ) {
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

        val externalUri =
            extractExternalAudioUri(intent)

        isHandlingExternalIntent =
            externalUri != null

        externalLaunchState.value =
            isHandlingExternalIntent

        setContent {
            com.rovia.music.theme.RoviaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    if (externalLaunchState.value) {
                        /*
                         * No separate loading screen or mode label.
                         *
                         * The existing Player surface appears after
                         * the selected URI has been resolved.
                         */
                        if (externalTrackState.value != null) {
                            ExternalPlayerHost(
                                playbackController =
                                    roviaApplication
                                        .externalPlaybackController,
                                lyricsRepository =
                                    roviaApplication
                                        .externalLyricsRepository,
                                audioOutputController =
                                    roviaApplication
                                        .externalAudioOutputController,
                                onClose = {
                                    closeExternalPlayback()
                                },
                                modifier =
                                    Modifier.fillMaxSize(),
                            )
                        }
                    } else {
                        /*
                         * The regular launcher flow remains unchanged.
                         * AppContainer is accessed only from this path.
                         */
                        var hasAudioPermission by
                            remember {
                                mutableStateOf(
                                    hasAudioPermission(),
                                )
                            }

                        val permissionLauncher =
                            rememberLauncherForActivityResult(
                                contract =
                                    ActivityResultContracts
                                        .RequestPermission(),
                            ) { granted ->
                                hasAudioPermission = granted

                                /*
                                 * Regular catalog synchronization
                                 * happens only after permission has
                                 * been granted in the normal app flow.
                                 */
                                if (
                                    granted &&
                                    !isHandlingExternalIntent
                                ) {
                                    roviaApplication
                                        .appContainer
                                        .synchronizeMusicCatalog()
                                }
                            }

                        LaunchedEffect(
                            hasAudioPermission,
                        ) {
                            if (
                                !hasAudioPermission &&
                                !isHandlingExternalIntent
                            ) {
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
                                audioOutputController =
                                    roviaApplication
                                        .appContainer
                                        .audioOutputController,
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
                                        R.string
                                            .permission_music_message,
                                    ),
                                )

                                Button(
                                    onClick = {
                                        permissionLauncher.launch(
                                            Manifest.permission
                                                .READ_MEDIA_AUDIO,
                                        )
                                    },
                                ) {
                                    Text(
                                        stringResource(
                                            R.string
                                                .permission_allow_music,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (externalUri != null) {
            /*
             * During configuration recreation, reuse the existing
             * in-memory external track instead of restarting it.
             */
            val existingTrack =
                if (
                    savedInstanceState != null &&
                    roviaApplication
                        .playbackContextState
                        .isExternalActive
                ) {
                    roviaApplication
                        .externalPlaybackController
                        .playbackState
                        .value
                        .currentTrack
                } else {
                    null
                }

            if (existingTrack != null) {
                externalTrackState.value =
                    existingTrack

                externalSessionStarted = true
            } else {
                startExternalPlayback(externalUri)
            }
        }
    }

    override fun onNewIntent(
        intent: Intent,
    ) {
        super.onNewIntent(intent)

        setIntent(intent)

        val externalUri =
            extractExternalAudioUri(intent)
                ?: return

        isHandlingExternalIntent = true
        externalLaunchState.value = true
        externalCloseHandled = false

        startExternalPlayback(externalUri)
    }

    /**
     * Resolves metadata directly from the caller-provided URI,
     * then starts playback through the external controller.
     *
     * It never accesses AppContainer, MusicRepository,
     * FolderFilterRepository, or catalog synchronization.
     */
    private fun startExternalPlayback(
        uri: Uri,
    ) {
        val roviaApplication =
            application as RoviaApplication

        val previousTrack =
            externalTrackState.value

        externalResolveJob?.cancel()

        externalTrackState.value = null

        externalResolveJob =
            lifecycleScope.launch {
                try {
                    val track =
                        roviaApplication
                            .externalAudioMetadataResolver
                            .resolve(uri)

                    if (
                        isFinishing ||
                        isDestroyed
                    ) {
                        roviaApplication
                            .externalAudioMetadataResolver
                            .releaseArtwork(track)

                        return@launch
                    }

                    roviaApplication
                        .externalPlaybackController
                        .startExternalPlayback(track)

                    externalSessionStarted = true

                    externalTrackState.value =
                        track

                    if (
                        previousTrack != null &&
                        previousTrack.uri != track.uri
                    ) {
                        roviaApplication
                            .externalAudioMetadataResolver
                            .releaseArtwork(previousTrack)
                    }
                } catch (
                    cancellation: CancellationException,
                ) {
                    throw cancellation
                } catch (
                    exception: Exception,
                ) {
                    Log.w(
                        TAG,
                        "Unable to open the selected external audio URI.",
                        exception,
                    )

                    externalTrackState.value = null

                    if (externalSessionStarted) {
                        roviaApplication
                            .externalPlaybackController
                            .endExternalPlayback()

                        externalSessionStarted = false
                    }

                    roviaApplication
                        .externalAudioMetadataResolver
                        .releaseAllArtwork()

                    if (!isFinishing) {
                        finish()
                    }
                }
            }
    }

    /**
     * Ends the external session and returns to the calling app.
     *
     * The regular player's queue is restored by the playback
     * service. This Activity does not clear that regular queue.
     */
    private fun closeExternalPlayback() {
        if (externalCloseHandled) {
            return
        }

        externalCloseHandled = true

        externalResolveJob?.cancel()
        externalResolveJob = null

        val roviaApplication =
            application as RoviaApplication

        if (externalSessionStarted) {
            roviaApplication
                .externalPlaybackController
                .endExternalPlayback()

            externalSessionStarted = false
        }

        roviaApplication
            .externalAudioMetadataResolver
            .releaseAllArtwork()

        externalTrackState.value = null

        finish()
    }

    override fun onDestroy() {
        /*
         * Safety cleanup if this external Activity is finished
         * without going through the normal Player close callback.
         *
         * Rotation must not terminate the active playback session.
         */
        if (
            isHandlingExternalIntent &&
            isFinishing &&
            !isChangingConfigurations &&
            !externalCloseHandled
        ) {
            externalResolveJob?.cancel()
            externalResolveJob = null

            val roviaApplication =
                application as RoviaApplication

            if (externalSessionStarted) {
                roviaApplication
                    .externalPlaybackController
                    .endExternalPlayback()

                externalSessionStarted = false
            }

            roviaApplication
                .externalAudioMetadataResolver
                .releaseAllArtwork()
        }

        super.onDestroy()
    }

    /**
     * Accepts only ACTION_VIEW requests for audio content URIs.
     *
     * The URI read grant is enforced by Android's content provider.
     * This method does not attempt to bypass Android permissions.
     */
    private fun extractExternalAudioUri(
        intent: Intent?,
    ): Uri? {
        if (intent?.action != Intent.ACTION_VIEW) {
            return null
        }

        val uri =
            intent.data
                ?: return null

        if (
            uri.scheme !=
                ContentResolver.SCHEME_CONTENT
        ) {
            return null
        }

        val intentMimeType =
            intent.type
                ?.takeIf {
                    it.startsWith(
                        AUDIO_MIME_PREFIX,
                        ignoreCase = true,
                    )
                }

        val providerMimeType =
            runCatching {
                contentResolver.getType(uri)
            }.getOrNull()
                ?.takeIf {
                    it.startsWith(
                        AUDIO_MIME_PREFIX,
                        ignoreCase = true,
                    )
                }

        if (
            intentMimeType == null &&
            providerMimeType == null
        ) {
            return null
        }

        return uri
    }

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_MEDIA_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        const val TAG =
            "RoviaMainActivity"

        const val AUDIO_MIME_PREFIX =
            "audio/"
    }
}
