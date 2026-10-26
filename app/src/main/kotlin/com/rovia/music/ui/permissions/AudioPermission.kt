package com.rovia.music.ui.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private const val AUDIO_PERMISSION_PREFERENCES = "rovia_audio_permission"
private const val KEY_AUDIO_PERMISSION_REQUESTED = "audio_permission_requested"

/**
 * Requests READ_MEDIA_AUDIO once, from the app entry point, so Android owns
 * the permission dialog instead of individual pages showing their own CTA.
 */
@Composable
fun RequestAudioPermissionOnFirstLaunch() {
    val context = LocalContext.current.applicationContext
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { _ ->
            // Permission state is refreshed by rememberAudioPermissionGranted()
            // when the activity returns to the foreground.
        },
    )

    LaunchedEffect(context) {
        val preferences = context.getSharedPreferences(
            AUDIO_PERMISSION_PREFERENCES,
            Context.MODE_PRIVATE,
        )
        val alreadyGranted = hasAudioReadPermission(context)

        if (alreadyGranted) {
            preferences.edit()
                .putBoolean(KEY_AUDIO_PERMISSION_REQUESTED, true)
                .apply()
        } else if (!preferences.getBoolean(KEY_AUDIO_PERMISSION_REQUESTED, false)) {
            // Persist before launch to avoid duplicate dialogs during recreation.
            preferences.edit()
                .putBoolean(KEY_AUDIO_PERMISSION_REQUESTED, true)
                .apply()
            permissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
        }
    }
}

/** Keeps page data loading in sync when the user answers the system permission dialog. */
@Composable
fun rememberAudioPermissionGranted(): Boolean {
    val context = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    var isGranted by remember(context) {
        mutableStateOf(hasAudioReadPermission(context))
    }

    DisposableEffect(context, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isGranted = hasAudioReadPermission(context)
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        isGranted = hasAudioReadPermission(context)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return isGranted
}

private fun hasAudioReadPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_MEDIA_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED
