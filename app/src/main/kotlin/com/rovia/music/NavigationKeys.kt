package com.rovia.music

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object Home : NavKey

@Serializable
data object Search : NavKey

@Serializable
data object Library : NavKey

@Serializable
data object Settings : NavKey

@Serializable
data object FolderFilter : NavKey

@Serializable
data object About : NavKey

@Serializable
data object Player : NavKey