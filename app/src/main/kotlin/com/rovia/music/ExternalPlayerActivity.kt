package com.rovia.music

import android.content.ContentResolver
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.rovia.music.feature.player.PlayerScreen
import com.rovia.music.playback.controller.PlaybackController
import com.rovia.music.playback.external.ExternalPlaybackCoordinator
import com.rovia.music.playback.external.ExternalPlaybackService
import com.rovia.music.ui.theme.RoviaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val STATE_LOADED_URI = "external_player_loaded_uri"

/**
 * The Open With entry point: shows the player screen for one external audio
 * file, played by [ExternalPlaybackService]. The file never enters the
 * library or any database, and the folder filter does not apply; the URI
 * grant from the calling app is all this activity uses. Leaving the activity
 * ends the playback; backgrounding it keeps playback running in the service.
 */
class ExternalPlayerActivity : ComponentActivity() {

    private lateinit var appContainer: AppContainer
    private lateinit var externalController: PlaybackController

    private var pendingUri: Uri? = null
    private var loadedUri: Uri? = null
    private var loadingUri: Uri? = null

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.enableEdgeToEdge(window)
        window.isNavigationBarContrastEnforced = false

        appContainer = AppContainer(applicationContext)
        externalController = PlaybackController(
            context = applicationContext,
            serviceClass = ExternalPlaybackService::class.java,
        )

        loadedUri = savedInstanceState
            ?.getString(STATE_LOADED_URI)
            ?.let(Uri::parse)
        onViewIntent(intent)
        observeController()

        setContent {
            RoviaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    // The player is a bottom sheet, as in the main app:
                    // swiping it down finishes this activity and ends playback.
                    val sheetState = rememberBottomSheetState(
                        initialValue = SheetValue.Expanded,
                        enabledValues = setOf(
                            SheetValue.Hidden,
                            SheetValue.Expanded,
                        ),
                    )
                    ModalBottomSheet(
                        onDismissRequest = { finish() },
                        modifier = Modifier.fillMaxSize(),
                        sheetState = sheetState,
                        sheetMaxWidth = Dp.Unspecified,
                        dragHandle = null,
                        contentWindowInsets = {
                            WindowInsets(0, 0, 0, 0)
                        },
                    ) {
                        PlayerScreen(
                            playbackController = externalController,
                            audioRepository = appContainer.mediaStoreAudioRepository,
                            onOpenArtist = {},
                            externalMode = true,
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        externalController.connect()
    }

    override fun onStop() {
        externalController.disconnect()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        onViewIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_LOADED_URI, (loadedUri ?: pendingUri)?.toString())
    }

    override fun onDestroy() {
        if (isFinishing) {
            externalController.mediaController.value?.let { controller ->
                runCatching {
                    controller.stop()
                    controller.clearMediaItems()
                }.onFailure { }
            }
            ExternalPlaybackCoordinator.onExternalSessionEnded()
            stopService(Intent(this, ExternalPlaybackService::class.java))
        }
        if (::appContainer.isInitialized) {
            appContainer.close()
        }
        super.onDestroy()
    }

    private fun onViewIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri == loadedUri || uri == pendingUri) return
        pendingUri = uri
        tryLoadPending()
    }

    private fun observeController() {
        lifecycleScope.launch {
            externalController.mediaController.collect { controller ->
                if (controller != null) {
                    tryLoadPending()
                }
            }
        }
    }

    private fun tryLoadPending() {
        val uri = pendingUri ?: return
        if (loadingUri == uri) return
        val controller = externalController.mediaController.value ?: return

        // After a configuration change the service may already hold this
        // file; do not restart it.
        if (controller.currentMediaItem?.localConfiguration?.uri == uri) {
            loadedUri = uri
            pendingUri = null
            return
        }

        loadingUri = uri
        lifecycleScope.launch {
            val item = withContext(Dispatchers.IO) {
                buildMediaItem(uri)
            }
            loadingUri = null
            if (pendingUri != uri) return@launch
            val activeController = externalController.mediaController.value
                ?: return@launch
            activeController.setMediaItem(item)
            activeController.prepare()
            activeController.play()
            loadedUri = uri
            pendingUri = null
        }
    }

    private fun buildMediaItem(uri: Uri): MediaItem {
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var durationMs: Long? = null

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(applicationContext, uri)
            fun metadata(key: Int): String? = retriever
                .extractMetadata(key)
                ?.trim()
                ?.takeIf(String::isNotEmpty)

            title = metadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = metadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: metadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            album = metadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.takeIf { it > 0L }
        } catch (_: Exception) {
            // Unreadable metadata: the file name below is the fallback.
        } finally {
            runCatching { retriever.release() }.onFailure { }
        }

        if (title == null) {
            title = displayName(uri)
                ?.substringBeforeLast('.')
                ?.takeIf(String::isNotBlank)
                ?: uri.lastPathSegment
        }

        val mediaMetadata = MediaMetadata.Builder().apply {
            title?.let { setTitle(it) }
            artist?.let { setArtist(it) }
            album?.let { setAlbumTitle(it) }
            durationMs?.let { setDurationMs(it) }
        }.build()

        return MediaItem.Builder()
            .setMediaId(uri.toString())
            .setUri(uri)
            .setMediaMetadata(mediaMetadata)
            .build()
    }

    private fun displayName(uri: Uri): String? {
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            try {
                contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) {
                            return cursor.getString(index)
                        }
                    }
                }
            } catch (_: Exception) {
                // Fall through to the path segment.
            }
            return null
        }
        return uri.lastPathSegment
    }
}
