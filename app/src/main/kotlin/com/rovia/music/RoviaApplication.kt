
package com.rovia.music

import android.app.Application
import com.rovia.music.core.library.LyricsRepository
import com.rovia.music.core.playback.AudioOutputController
import com.rovia.music.core.playback.PlaybackContextState
import com.rovia.music.data.media.store.EmbeddedLyricsRepository
import com.rovia.music.data.media.store.ExternalAudioMetadataResolver
import com.rovia.music.playback.media3.AndroidAudioOutputController
import com.rovia.music.playback.media3.ExternalMedia3PlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class RoviaApplication : Application() {

    /**
     * Application-wide coroutine scope.
     *
     * This scope is created only when first accessed.
     */
    val applicationScope: CoroutineScope by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED,
    ) {
        CoroutineScope(
            SupervisorJob() + Dispatchers.Default,
        )
    }

    /**
     * Shared in-memory state for regular and external playback.
     *
     * This instance does not initialize Room or the music catalog.
     */
    val playbackContextState: PlaybackContextState by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED,
    ) {
        PlaybackContextState()
    }

    /**
     * One application-scoped audio-output controller.
     *
     * Regular playback and external playback share this instance
     * for consistent output observation and system media volume.
     */
    val audioOutputController: AudioOutputController by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED,
    ) {
        AndroidAudioOutputController(
            context = applicationContext,
        )
    }

    /**
     * Compatibility accessor for the existing external Player host.
     *
     * This returns the same instance as audioOutputController.
     */
    val externalAudioOutputController: AudioOutputController
        get() = audioOutputController

    /**
     * Regular application dependencies.
     *
     * AppContainer remains lazy so external playback does not
     * initialize Room or the regular music catalog by itself.
     */
    val appContainer: AppContainer by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED,
    ) {
        AppContainer(
            context = this,
            applicationScope = applicationScope,
            playbackContextState = playbackContextState,
            sharedAudioOutputController = audioOutputController,
        )
    }

    /**
     * Resolves metadata directly from an external content URI.
     *
     * This resolver does not access AppContainer or the catalog.
     */
    val externalAudioMetadataResolver: ExternalAudioMetadataResolver by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED,
    ) {
        ExternalAudioMetadataResolver(
            context = applicationContext,
        )
    }

    /**
     * Repository for reading embedded local lyrics without Room.
     */
    val externalLyricsRepository: LyricsRepository by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED,
    ) {
        EmbeddedLyricsRepository(
            context = applicationContext,
        )
    }

    /**
     * Controller dedicated to external playback.
     *
     * It receives the same PlaybackContextState used by the
     * regular AppContainer playback controller.
     */
    val externalPlaybackController: ExternalMedia3PlaybackController by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED,
    ) {
        ExternalMedia3PlaybackController(
            context = applicationContext,
            playbackContextState = playbackContextState,
        )
    }

    override fun onCreate() {
        super.onCreate()

        /*
         * Do not eagerly initialize AppContainer here.
         *
         * MainActivity must route external file intents before
         * accessing regular dependencies or synchronizing the catalog.
         */
    }
}
