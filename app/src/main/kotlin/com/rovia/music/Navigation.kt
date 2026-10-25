package com.rovia.music

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.rovia.music.feature.home.HomeRecentlyAddedScreen
import com.rovia.music.feature.home.HomeScreen
import com.rovia.music.feature.library.LibraryScreen
import com.rovia.music.feature.player.PlayerScreen
import com.rovia.music.feature.search.SearchScreen
import com.rovia.music.feature.settings.AboutScreen
import com.rovia.music.feature.settings.FolderFilterScreen
import com.rovia.music.feature.settings.SettingsScreen
import com.rovia.music.ui.components.MiniPlayer
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private data object HomeDestination

private data object HomeRecentlyAddedDestination

private data object SearchDestination

private data object LibraryDestination

private data object SettingsDestination

private data object FolderFilterDestination

private data object AboutDestination

private val NavigationBarOverlayHeight = 80.dp

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
fun Navigation(appContainer: AppContainer) {
    val backStack = rememberSaveable(
        saver = NavigationBackStackSaver,
    ) {
        mutableStateListOf<Any>(HomeDestination)
    }

    val motionScheme = MaterialTheme.motionScheme
    val density = LocalDensity.current
    val eightDpInPixels = with(density) { 8.dp.toPx() }

    var topLevelSlideDirection by remember {
        mutableIntStateOf(0)
    }

    var showPlayerSheet by rememberSaveable {
        mutableStateOf(false)
    }

    val coroutineScope = rememberCoroutineScope()

    val playerSheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(
            SheetValue.Hidden,
            SheetValue.Expanded,
        ),
    )

    val navigationBarBottomInset = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()

    val topLevelTabTransition: (Int) -> ContentTransform = { direction ->
        when {
            direction > 0 -> {
                slideInHorizontally(
                    initialOffsetX = { fullWidth -> fullWidth },
                    animationSpec = motionScheme.defaultSpatialSpec(),
                ) togetherWith slideOutHorizontally(
                    targetOffsetX = { fullWidth -> -fullWidth },
                    animationSpec = motionScheme.defaultSpatialSpec(),
                )
            }

            direction < 0 -> {
                slideInHorizontally(
                    initialOffsetX = { fullWidth -> -fullWidth },
                    animationSpec = motionScheme.defaultSpatialSpec(),
                ) togetherWith slideOutHorizontally(
                    targetOffsetX = { fullWidth -> fullWidth },
                    animationSpec = motionScheme.defaultSpatialSpec(),
                )
            }

            else -> {
                EnterTransition.None togetherWith ExitTransition.None
            }
        }
    }

    val horizontalSlideTransitionMetadata =
        NavDisplay.transitionSpec {
            slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = motionScheme.defaultSpatialSpec(),
            ) togetherWith slideOutHorizontally(
                targetOffsetX = { fullWidth -> -fullWidth },
                animationSpec = motionScheme.defaultSpatialSpec(),
            )
        } + NavDisplay.popTransitionSpec {
            slideInHorizontally(
                initialOffsetX = { fullWidth -> -fullWidth },
                animationSpec = motionScheme.defaultSpatialSpec(),
            ) togetherWith slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = motionScheme.defaultSpatialSpec(),
            )
        }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        NavDisplay(
            backStack = backStack,
            onBack = {
                popBackStackSafely(backStack)
            },
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                EnterTransition.None togetherWith ExitTransition.None
            },
            popTransitionSpec = {
                EnterTransition.None togetherWith ExitTransition.None
            },
            predictivePopTransitionSpec = {
                EnterTransition.None togetherWith (
                    scaleOut(
                        targetScale = 0.9f,
                        transformOrigin = TransformOrigin.Center,
                        animationSpec = motionScheme.defaultSpatialSpec(),
                    ) +
                        slideOutHorizontally(
                            targetOffsetX = { fullWidth ->
                                ((fullWidth * 0.05f) - eightDpInPixels)
                                    .coerceAtLeast(0f)
                                    .roundToInt()
                            },
                            animationSpec = motionScheme.defaultSpatialSpec(),
                        ) +
                        fadeOut(
                            targetAlpha = 0f,
                            animationSpec = motionScheme.defaultSpatialSpec(),
                        )
                    )
            },
            entryProvider = entryProvider {
                entry<HomeDestination>(
                    metadata = NavDisplay.transitionSpec {
                        topLevelTabTransition(topLevelSlideDirection)
                    },
                ) {
                    NavigationDestinationSurface {
                        HomeScreen(
                            audioRepository =
                                appContainer.mediaStoreAudioRepository,
                            playbackController =
                                appContainer.playbackController,
                            onOpenRecentlyAdded = {
                                if (
                                    backStack.lastOrNull() !=
                                    HomeRecentlyAddedDestination
                                ) {
                                    backStack.add(
                                        HomeRecentlyAddedDestination,
                                    )
                                }
                            },
                            onOpenSettings = {
                                if (
                                    backStack.lastOrNull() !=
                                    SettingsDestination
                                ) {
                                    backStack.add(SettingsDestination)
                                }
                            },
                        )
                    }
                }

                entry<HomeRecentlyAddedDestination>(
                    metadata = horizontalSlideTransitionMetadata,
                ) {
                    NavigationDestinationSurface {
                        HomeRecentlyAddedScreen(
                            audioRepository =
                                appContainer.mediaStoreAudioRepository,
                            playbackController =
                                appContainer.playbackController,
                            onBack = {
                                popBackStackSafely(backStack)
                            },
                        )
                    }
                }

                entry<SearchDestination>(
                    metadata = NavDisplay.transitionSpec {
                        topLevelTabTransition(topLevelSlideDirection)
                    },
                ) {
                    NavigationDestinationSurface {
                        SearchScreen(
                            audioRepository =
                                appContainer.mediaStoreAudioRepository,
                            playbackController =
                                appContainer.playbackController,
                            onOpenSettings = {
                                if (
                                    backStack.lastOrNull() !=
                                    SettingsDestination
                                ) {
                                    backStack.add(SettingsDestination)
                                }
                            },
                        )
                    }
                }

                entry<LibraryDestination>(
                    metadata = NavDisplay.transitionSpec {
                        topLevelTabTransition(topLevelSlideDirection)
                    },
                ) {
                    NavigationDestinationSurface {
                        LibraryScreen(
                            audioRepository =
                                appContainer.mediaStoreAudioRepository,
                            playbackController =
                                appContainer.playbackController,
                            onOpenSettings = {
                                if (
                                    backStack.lastOrNull() !=
                                    SettingsDestination
                                ) {
                                    backStack.add(SettingsDestination)
                                }
                            },
                        )
                    }
                }

                entry<SettingsDestination>(
                    metadata = horizontalSlideTransitionMetadata,
                ) {
                    NavigationDestinationSurface {
                        SettingsScreen(
                            onBack = {
                                popBackStackSafely(backStack)
                            },
                            onFolderFilterClick = {
                                if (
                                    backStack.lastOrNull() !=
                                    FolderFilterDestination
                                ) {
                                    backStack.add(FolderFilterDestination)
                                }
                            },
                            onAboutClick = {
                                if (
                                    backStack.lastOrNull() !=
                                    AboutDestination
                                ) {
                                    backStack.add(AboutDestination)
                                }
                            },
                        )
                    }
                }

                entry<FolderFilterDestination>(
                    metadata = horizontalSlideTransitionMetadata,
                ) {
                    NavigationDestinationSurface {
                        FolderFilterScreen(
                            folderFilterRepository =
                                appContainer.folderFilterRepository,
                            folderScannerRepository =
                                appContainer.mediaStoreFolderRepository,
                            onBack = {
                                popBackStackSafely(backStack)
                            },
                        )
                    }
                }

                entry<AboutDestination>(
                    metadata = horizontalSlideTransitionMetadata,
                ) {
                    NavigationDestinationSurface {
                        AboutScreen(
                            onBack = {
                                popBackStackSafely(backStack)
                            },
                        )
                    }
                }
            },
        )

        NavigationBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(
                alpha = 0.78f,
            ),
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
            windowInsets = NavigationBarDefaults.windowInsets,
        ) {
            NavigationBarItem(
                selected = backStack.firstOrNull() == HomeDestination,
                onClick = {
                    topLevelSlideDirection =
                        calculateTopLevelSlideDirection(
                            currentDestination = backStack.firstOrNull(),
                            targetDestination = HomeDestination,
                        )

                    navigateToTopLevel(
                        backStack = backStack,
                        destination = HomeDestination,
                    )
                },
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_home),
                        contentDescription = null,
                    )
                },
                label = {
                    Text(
                        text = stringResource(R.string.navigation_home),
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor =
                        MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor =
                        MaterialTheme.colorScheme.secondaryContainer,
                    unselectedIconColor =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )

            NavigationBarItem(
                selected = backStack.firstOrNull() == SearchDestination,
                onClick = {
                    topLevelSlideDirection =
                        calculateTopLevelSlideDirection(
                            currentDestination = backStack.firstOrNull(),
                            targetDestination = SearchDestination,
                        )

                    navigateToTopLevel(
                        backStack = backStack,
                        destination = SearchDestination,
                    )
                },
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_search),
                        contentDescription = null,
                    )
                },
                label = {
                    Text(
                        text = stringResource(R.string.navigation_search),
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor =
                        MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor =
                        MaterialTheme.colorScheme.secondaryContainer,
                    unselectedIconColor =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )

            NavigationBarItem(
                selected = backStack.firstOrNull() == LibraryDestination,
                onClick = {
                    topLevelSlideDirection =
                        calculateTopLevelSlideDirection(
                            currentDestination = backStack.firstOrNull(),
                            targetDestination = LibraryDestination,
                        )

                    navigateToTopLevel(
                        backStack = backStack,
                        destination = LibraryDestination,
                    )
                },
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_library),
                        contentDescription = null,
                    )
                },
                label = {
                    Text(
                        text = stringResource(R.string.navigation_library),
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor =
                        MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor =
                        MaterialTheme.colorScheme.secondaryContainer,
                    unselectedIconColor =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }

        if (!showPlayerSheet) {
            MiniPlayer(
                playbackController = appContainer.playbackController,
                onOpenPlayer = {
                    showPlayerSheet = true
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        bottom = NavigationBarOverlayHeight +
                            navigationBarBottomInset,
                    ),
            )
        }

        if (showPlayerSheet) {
            ModalBottomSheet(
                onDismissRequest = {
                    showPlayerSheet = false
                },
                modifier = Modifier.fillMaxSize(),
                sheetState = playerSheetState,
                sheetMaxWidth = Dp.Unspecified,
                dragHandle = null,
                contentWindowInsets = {
                    WindowInsets(0, 0, 0, 0)
                },
            ) {
                PlayerScreen(
                    playbackController = appContainer.playbackController,
                    onBack = {
                        coroutineScope
                            .launch {
                                playerSheetState.hide()
                            }
                            .invokeOnCompletion {
                                if (!playerSheetState.isVisible) {
                                    showPlayerSheet = false
                                }
                            }
                    },
                )
            }
        }
    }
}

@Composable
private fun NavigationDestinationSurface(
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        content()
    }
}

private val NavigationBackStackSaver =
    listSaver<SnapshotStateList<Any>, String>(
        save = { backStack ->
            backStack.map(::savedDestinationKey)
        },
        restore = { savedKeys ->
            mutableStateListOf<Any>().apply {
                savedKeys
                    .mapNotNull(::destinationFromSavedKey)
                    .forEach { destination ->
                        add(destination)
                    }

                if (isEmpty()) {
                    add(HomeDestination)
                }
            }
        },
    )

private fun savedDestinationKey(destination: Any): String =
    when (destination) {
        HomeDestination -> "home"
        HomeRecentlyAddedDestination -> "home_recently_added"
        SearchDestination -> "search"
        LibraryDestination -> "library"
        SettingsDestination -> "settings"
        FolderFilterDestination -> "folder_filter"
        AboutDestination -> "about"
        else -> "home"
    }

private fun destinationFromSavedKey(key: String): Any? =
    when (key) {
        "home" -> HomeDestination
        "home_recently_added" -> HomeRecentlyAddedDestination
        "search" -> SearchDestination
        "library" -> LibraryDestination
        "settings" -> SettingsDestination
        "folder_filter" -> FolderFilterDestination
        "about" -> AboutDestination
        else -> null
    }

private fun calculateTopLevelSlideDirection(
    currentDestination: Any?,
    targetDestination: Any,
): Int {
    fun destinationIndex(destination: Any?): Int? =
        when (destination) {
            HomeDestination -> 0
            SearchDestination -> 1
            LibraryDestination -> 2
            else -> null
        }

    val currentIndex = destinationIndex(currentDestination)
    val targetIndex = destinationIndex(targetDestination)

    return when {
        currentIndex == null || targetIndex == null -> 0
        targetIndex > currentIndex -> 1
        targetIndex < currentIndex -> -1
        else -> 0
    }
}

private fun popBackStackSafely(
    backStack: SnapshotStateList<Any>,
) {
    if (backStack.size > 1) {
        backStack.removeLastOrNull()
    }
}

private fun navigateToTopLevel(
    backStack: SnapshotStateList<Any>,
    destination: Any,
) {
    if (
        backStack.size == 1 &&
        backStack.firstOrNull() == destination
    ) {
        return
    }

    backStack.clear()
    backStack.add(destination)
}