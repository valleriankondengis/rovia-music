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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.rovia.music.feature.album.AlbumPage
import com.rovia.music.feature.artist.ArtistPage
import com.rovia.music.feature.genre.GenrePage
import com.rovia.music.feature.single.SinglePage
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
import kotlinx.serialization.Serializable

@Serializable
private data object HomeDestination : NavKey

@Serializable
private data object HomeRecentlyAddedDestination : NavKey

@Serializable
private data object SearchDestination : NavKey

@Serializable
private data object LibraryDestination : NavKey

@Serializable
private data class ArtistDestination(
    val artistName: String,
    val owner: TopLevelSection = TopLevelSection.LIBRARY,
) : NavKey

@Serializable
private data class SingleDestination(
    val releaseKey: String,
    val owner: TopLevelSection = TopLevelSection.LIBRARY,
) : NavKey

@Serializable
private data class AlbumDestination(
    val releaseKey: String,
    val owner: TopLevelSection = TopLevelSection.LIBRARY,
) : NavKey

@Serializable
private data class GenreDestination(
    val genreKey: String,
    val owner: TopLevelSection = TopLevelSection.LIBRARY,
) : NavKey

@Serializable
private data class ArtistGenreDestination(
    val artistName: String,
    val genreKey: String,
    val owner: TopLevelSection = TopLevelSection.LIBRARY,
) : NavKey

@Serializable
private enum class TopLevelSection {
    HOME,
    SEARCH,
    LIBRARY,
}

@Serializable
private data class SettingsDestination(val owner: TopLevelSection) : NavKey

@Serializable
private data class FolderFilterDestination(val owner: TopLevelSection) : NavKey

@Serializable
private data class AboutDestination(val owner: TopLevelSection) : NavKey

private val NavigationBarOverlayHeight = 80.dp

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
fun Navigation(appContainer: AppContainer) {
    val navigationState = rememberRoviaNavigationState()

    val motionScheme = MaterialTheme.motionScheme
    val density = LocalDensity.current
    val eightDpInPixels = with(density) { 8.dp.toPx() }

    var topLevelSlideDirection by remember {
        mutableIntStateOf(0)
    }

    var showPlayerSheet by rememberSaveable {
        mutableStateOf(false)
    }

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

    val routeEntryProvider = entryProvider<NavKey> {
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
                        navigationState.navigateWithin(
                            owner = TopLevelSection.HOME,
                            destination = HomeRecentlyAddedDestination,
                        )
                    },
                    onOpenSettings = {
                        navigationState.navigateWithin(
                            owner = TopLevelSection.HOME,
                            destination = SettingsDestination(TopLevelSection.HOME),
                        )
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
                        navigationState.goBack(TopLevelSection.HOME)
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
                        navigationState.navigateWithin(
                            owner = TopLevelSection.SEARCH,
                            destination = SettingsDestination(TopLevelSection.SEARCH),
                        )
                    },
                    onOpenArtist = { artistName ->
                        navigationState.navigateWithin(
                            owner = TopLevelSection.SEARCH,
                            destination = ArtistDestination(
                                artistName = artistName,
                                owner = TopLevelSection.SEARCH,
                            ),
                        )
                    },
                    onOpenAlbum = { releaseKey ->
                        navigationState.navigateWithin(
                            owner = TopLevelSection.SEARCH,
                            destination = AlbumDestination(
                                releaseKey = releaseKey,
                                owner = TopLevelSection.SEARCH,
                            ),
                        )
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
                    onOpenArtist = { artistName ->
                        navigationState.navigateWithin(
                            owner = TopLevelSection.LIBRARY,
                            destination = ArtistDestination(artistName),
                        )
                    },
                    onOpenAlbum = { releaseKey ->
                        navigationState.navigateWithin(
                            owner = TopLevelSection.LIBRARY,
                            destination = AlbumDestination(releaseKey),
                        )
                    },
                    onOpenGenre = { genreKey ->
                        navigationState.navigateWithin(
                            owner = TopLevelSection.LIBRARY,
                            destination = GenreDestination(genreKey),
                        )
                    },
                    onOpenSettings = {
                        navigationState.navigateWithin(
                            owner = TopLevelSection.LIBRARY,
                            destination = SettingsDestination(TopLevelSection.LIBRARY),
                        )
                    },
                )
            }
        }

        entry<ArtistDestination>(
            metadata = horizontalSlideTransitionMetadata,
        ) { destination ->
            val catalog by appContainer.mediaStoreAudioRepository.catalogState
                .collectAsStateWithLifecycle()

            NavigationDestinationSurface {
                ArtistPage(
                    artistName = destination.artistName,
                    tracks = catalog.tracks,
                    playbackController = appContainer.playbackController,
                    onBack = {
                        navigationState.goBack(destination.owner)
                    },
                    onOpenSingle = { releaseKey ->
                        navigationState.navigateWithin(
                            owner = destination.owner,
                            destination = SingleDestination(
                                releaseKey = releaseKey,
                                owner = destination.owner,
                            ),
                        )
                    },
                    onOpenAlbum = { releaseKey ->
                        navigationState.navigateWithin(
                            owner = destination.owner,
                            destination = AlbumDestination(
                                releaseKey = releaseKey,
                                owner = destination.owner,
                            ),
                        )
                    },
                    onOpenGenre = { genreKey ->
                        navigationState.navigateWithin(
                            owner = destination.owner,
                            destination = ArtistGenreDestination(
                                artistName = destination.artistName,
                                genreKey = genreKey,
                                owner = destination.owner,
                            ),
                        )
                    },
                    songsAndEpsTitleRes = R.string.artist_songs_and_eps,
                )
            }
        }

        entry<SingleDestination>(
            metadata = horizontalSlideTransitionMetadata,
        ) { destination ->
            val catalog by appContainer.mediaStoreAudioRepository.catalogState
                .collectAsStateWithLifecycle()

            NavigationDestinationSurface {
                SinglePage(
                    releaseKey = destination.releaseKey,
                    catalogTracks = catalog.tracks,
                    playbackController = appContainer.playbackController,
                    onBack = {
                        navigationState.goBack(destination.owner)
                    },
                )
            }
        }

        entry<AlbumDestination>(
            metadata = horizontalSlideTransitionMetadata,
        ) { destination ->
            val catalog by appContainer.mediaStoreAudioRepository.catalogState
                .collectAsStateWithLifecycle()

            NavigationDestinationSurface {
                AlbumPage(
                    releaseKey = destination.releaseKey,
                    catalogTracks = catalog.tracks,
                    playbackController = appContainer.playbackController,
                    onBack = {
                        navigationState.goBack(destination.owner)
                    },
                )
            }
        }

        entry<GenreDestination>(
            metadata = horizontalSlideTransitionMetadata,
        ) { destination ->
            val catalog by appContainer.mediaStoreAudioRepository.catalogState
                .collectAsStateWithLifecycle()

            NavigationDestinationSurface {
                GenrePage(
                    genreKey = destination.genreKey,
                    catalogTracks = catalog.tracks,
                    playbackController = appContainer.playbackController,
                    onBack = {
                        navigationState.goBack(destination.owner)
                    },
                )
            }
        }

        entry<ArtistGenreDestination>(
            metadata = horizontalSlideTransitionMetadata,
        ) { destination ->
            val catalog by appContainer.mediaStoreAudioRepository.catalogState
                .collectAsStateWithLifecycle()

            NavigationDestinationSurface {
                GenrePage(
                    genreKey = destination.genreKey,
                    artistName = destination.artistName,
                    catalogTracks = catalog.tracks,
                    playbackController = appContainer.playbackController,
                    onBack = {
                        navigationState.goBack(destination.owner)
                    },
                )
            }
        }

        entry<SettingsDestination>(
            metadata = horizontalSlideTransitionMetadata,
        ) { destination ->
            NavigationDestinationSurface {
                SettingsScreen(
                    onBack = {
                        navigationState.goBack(destination.owner)
                    },
                    onFolderFilterClick = {
                        navigationState.navigateWithin(
                            owner = destination.owner,
                            destination = FolderFilterDestination(destination.owner),
                        )
                    },
                    onAboutClick = {
                        navigationState.navigateWithin(
                            owner = destination.owner,
                            destination = AboutDestination(destination.owner),
                        )
                    },
                )
            }
        }

        entry<FolderFilterDestination>(
            metadata = horizontalSlideTransitionMetadata,
        ) { destination ->
            NavigationDestinationSurface {
                FolderFilterScreen(
                    folderFilterRepository =
                        appContainer.folderFilterRepository,
                    folderScannerRepository =
                        appContainer.mediaStoreFolderRepository,
                    onBack = {
                        navigationState.goBack(destination.owner)
                    },
                )
            }
        }

        entry<AboutDestination>(
            metadata = horizontalSlideTransitionMetadata,
        ) { destination ->
            NavigationDestinationSurface {
                AboutScreen(
                    onBack = {
                        navigationState.goBack(destination.owner)
                    },
                )
            }
        }
    }
    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        NavDisplay(
            entries = navigationState.toDecoratedEntries(routeEntryProvider),
            onBack = {
                navigationState.goBack()
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
                selected = navigationState.topLevelSection == TopLevelSection.HOME,
                onClick = {
                    topLevelSlideDirection =
                        calculateTopLevelSlideDirection(
                            currentDestination = navigationState.topLevelRoute,
                            targetDestination = HomeDestination,
                        )

                    navigationState.selectTopLevel(TopLevelSection.HOME)
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
                selected = navigationState.topLevelSection == TopLevelSection.SEARCH,
                onClick = {
                    topLevelSlideDirection =
                        calculateTopLevelSlideDirection(
                            currentDestination = navigationState.topLevelRoute,
                            targetDestination = SearchDestination,
                        )

                    navigationState.selectTopLevel(TopLevelSection.SEARCH)
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
                selected = navigationState.topLevelSection == TopLevelSection.LIBRARY,
                onClick = {
                    topLevelSlideDirection =
                        calculateTopLevelSlideDirection(
                            currentDestination = navigationState.topLevelRoute,
                            targetDestination = LibraryDestination,
                        )

                    navigationState.selectTopLevel(TopLevelSection.LIBRARY)
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
                    onOpenArtist = { artistName ->
                        // Navigate through the existing Library stack so ArtistPage's Back
                        // behavior and the saved Library state remain consistent.
                        topLevelSlideDirection = calculateTopLevelSlideDirection(
                            currentDestination = navigationState.topLevelRoute,
                            targetDestination = LibraryDestination,
                        )
                        navigationState.navigateWithin(
                            owner = TopLevelSection.LIBRARY,
                            destination = ArtistDestination(artistName),
                        )
                        navigationState.selectTopLevel(TopLevelSection.LIBRARY)
                        showPlayerSheet = false
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

@Composable
private fun rememberRoviaNavigationState(): RoviaNavigationState {
    val homeBackStack: NavBackStack<NavKey> = rememberNavBackStack(HomeDestination)
    val searchBackStack: NavBackStack<NavKey> = rememberNavBackStack(SearchDestination)
    val libraryBackStack: NavBackStack<NavKey> = rememberNavBackStack(LibraryDestination)
    val topLevelSectionState = rememberSaveable {
        mutableStateOf(TopLevelSection.HOME)
    }

    val backStacks = remember(homeBackStack, searchBackStack, libraryBackStack) {
        linkedMapOf<NavKey, NavBackStack<NavKey>>(
            HomeDestination to homeBackStack,
            SearchDestination to searchBackStack,
            LibraryDestination to libraryBackStack,
        )
    }

    return remember(backStacks, topLevelSectionState) {
        RoviaNavigationState(
            backStacks = backStacks,
            topLevelSectionState = topLevelSectionState,
        )
    }
}

/**
 * Keeps one small key stack per top-level tab. Only saveable UI state is retained for
 * off-screen entries; their compositions and non-saveable objects (such as artwork bitmaps)
 * are disposed when the corresponding entries are not displayed.
 */
private class RoviaNavigationState(
    val backStacks: Map<NavKey, NavBackStack<NavKey>>,
    private val topLevelSectionState: MutableState<TopLevelSection>,
) {
    val topLevelSection: TopLevelSection
        get() = topLevelSectionState.value

    val topLevelRoute: NavKey
        get() = topLevelSection.asDestination()

    fun selectTopLevel(section: TopLevelSection) {
        topLevelSectionState.value = section
    }

    fun navigateWithin(
        owner: TopLevelSection,
        destination: NavKey,
    ) {
        val stack = backStacks[owner.asDestination()] ?: return
        if (stack.lastOrNull() != destination) {
            stack.add(destination)
        }
    }

    fun goBack(owner: TopLevelSection = topLevelSection) {
        val stack = backStacks[owner.asDestination()] ?: return
        if (stack.size > 1) {
            stack.removeLastOrNull()
        } else if (topLevelSection == owner && owner != TopLevelSection.HOME) {
            // Keep the previous state of each tab, but follow the standard exit-through-home pattern.
            topLevelSectionState.value = TopLevelSection.HOME
        }
    }

    @Composable
    fun toDecoratedEntries(
        entryProvider: (NavKey) -> NavEntry<NavKey>,
    ): List<NavEntry<NavKey>> {
        // Each tab gets its own native Navigation 3 saveable-state holder. This prevents
        // duplicate entry keys from sharing UI state when the same settings page exists in
        // more than one top-level stack.
        val decoratedEntries = backStacks.mapValues { (_, stack) ->
            rememberDecoratedNavEntries(
                backStack = stack,
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
                ),
                entryProvider = entryProvider,
            )
        }

        val activeRoute = topLevelRoute
        val routesInUse = if (activeRoute == HomeDestination) {
            listOf(HomeDestination)
        } else {
            listOf(HomeDestination, activeRoute)
        }

        return routesInUse.flatMap { route ->
            decoratedEntries[route].orEmpty()
        }
    }
}

private fun TopLevelSection.asDestination(): NavKey = when (this) {
    TopLevelSection.HOME -> HomeDestination
    TopLevelSection.SEARCH -> SearchDestination
    TopLevelSection.LIBRARY -> LibraryDestination
}