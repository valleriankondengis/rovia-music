
package com.rovia.music.core.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Identifies the playback context currently controlled by Rovia.
 *
 * REGULAR represents the normal in-app playback session.
 * EXTERNAL represents playback initiated through an external
 * content URI, such as a file selected in an Android file manager.
 */
enum class PlaybackContext {
    REGULAR,
    EXTERNAL,
}

/**
 * Holds the active playback context in memory.
 *
 * This state is intentionally not persisted. It allows the regular
 * and external playback controllers to coordinate event handling
 * without storing temporary playback information in the database.
 *
 * The same instance must be shared by the application components
 * that need to observe or change the active playback context.
 */
class PlaybackContextState(
    initialContext: PlaybackContext = PlaybackContext.REGULAR,
) {

    private val mutableActiveContext =
        MutableStateFlow(initialContext)

    /**
     * Observable active playback context.
     */
    val activeContext: StateFlow<PlaybackContext> =
        mutableActiveContext.asStateFlow()

    /**
     * Returns true when external playback is the active context.
     */
    val isExternalActive: Boolean
        get() = mutableActiveContext.value == PlaybackContext.EXTERNAL

    /**
     * Updates the active playback context.
     *
     * This changes in-memory coordination state only.
     * It does not start, pause, or stop a player by itself.
     */
    fun setActiveContext(
        context: PlaybackContext,
    ) {
        mutableActiveContext.value = context
    }
}
