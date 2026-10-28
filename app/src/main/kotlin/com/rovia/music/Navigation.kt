package com.rovia.music

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.navigationevent.NavigationEvent
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

private val FloatingToolbarOverlayHeight = 80.dp

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

    val predictiveBackTransition: (Int) -> ContentTransform = { swipeEdge ->
        val isRightEdge = swipeEdge == NavigationEvent.EDGE_RIGHT
        val transformOrigin = if (isRightEdge) {
            TransformOrigin(0f, 0.5f)
        } else {
            TransformOrigin(1f, 0.5f)
        }

        EnterTransition.None togetherWith (
            scaleOut(
                targetScale = 0.9f,
                transformOrigin = transformOrigin,
                animationSpec = tween(
                    durationMillis = 300,
                    easing = LinearEasing,
                ),
            ) +
                slideOutHorizontally(
                    targetOffsetX = { fullWidth ->
                        val offset = ((fullWidth * 0.05f) - eightDpInPixels)
                            .coerceAtLeast(0f)
                            .roundToInt()
                        if (isRightEdge) -offset else offset
                    },
                    animationSpec = tween(
                        durationMillis = 300,
                        easing = LinearEasing,
                    ),
                )
        )
    }

    val routeEntryProvider = entryProvider<NavKey> {
        entry<HomeDestination>(clazzContentKey = { key -> navigationContentKey(key) }) {
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

        entry<HomeRecentlyAddedDestination>(clazzContentKey = { key -> navigationContentKey(key) }) {
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

        entry<SearchDestination>(clazzContentKey = { key -> navigationContentKey(key) }) {
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

        entry<LibraryDestination>(clazzContentKey = { key -> navigationContentKey(key) }) {
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

        entry<ArtistDestination>(clazzContentKey = { key -> navigationContentKey(key) }) { destination ->
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

        entry<SingleDestination>(clazzContentKey = { key -> navigationContentKey(key) }) { destination ->
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

        entry<AlbumDestination>(clazzContentKey = { key -> navigationContentKey(key) }) { destination ->
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

        entry<GenreDestination>(clazzContentKey = { key -> navigationContentKey(key) }) { destination ->
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

        entry<ArtistGenreDestination>(clazzContentKey = { key -> navigationContentKey(key) }) { destination ->
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

        entry<SettingsDestination>(clazzContentKey = { key -> navigationContentKey(key) }) { destination ->
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

        entry<FolderFilterDestination>(clazzContentKey = { key -> navigationContentKey(key) }) { destination ->
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

        entry<AboutDestination>(clazzContentKey = { key -> navigationContentKey(key) }) { destination ->
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
                slideInHorizontally(
                    initialOffsetX = { fullWidth -> fullWidth },
                    animationSpec = motionScheme.defaultSpatialSpec(),
                ) togetherWith slideOutHorizontally(
                    targetOffsetX = { fullWidth -> -fullWidth },
                    animationSpec = motionScheme.defaultSpatialSpec(),
                )
            },
            popTransitionSpec = {
                slideInHorizontally(
                    initialOffsetX = { fullWidth -> -fullWidth },
                    animationSpec = motionScheme.defaultSpatialSpec(),
                ) togetherWith slideOutHorizontally(
                    targetOffsetX = { fullWidth -> fullWidth },
                    animationSpec = motionScheme.defaultSpatialSpec(),
                )
            },
            predictivePopTransitionSpec = { swipeEdge ->
                predictiveBackTransition(swipeEdge)
            },
        )

        HorizontalFloatingToolbar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp),
            expanded = true,
            colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
                toolbarContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            content = {
                val shouldHideLabels = LocalDensity.current.fontScale > 1.25f

                FloatingNavigationItem(
                    selected = navigationState.isTopLevelSectionSelected(TopLevelSection.HOME),
                    iconRes = R.drawable.ic_home,
                    labelRes = R.string.navigation_home,
                    shouldHideLabel = shouldHideLabels,
                    onClick = {
                        navigationState.selectTopLevel(TopLevelSection.HOME)
                    },
                )

                Spacer(Modifier.width(8.dp))

                FloatingNavigationItem(
                    selected = navigationState.isTopLevelSectionSelected(TopLevelSection.SEARCH),
                    iconRes = R.drawable.ic_search,
                    labelRes = R.string.navigation_search,
                    shouldHideLabel = shouldHideLabels,
                    onClick = {
                        navigationState.selectTopLevel(TopLevelSection.SEARCH)
                    },
                )

                Spacer(Modifier.width(8.dp))

                FloatingNavigationItem(
                    selected = navigationState.isTopLevelSectionSelected(TopLevelSection.LIBRARY),
                    iconRes = R.drawable.ic_library,
                    labelRes = R.string.navigation_library,
                    shouldHideLabel = shouldHideLabels,
                    onClick = {
                        navigationState.selectTopLevel(TopLevelSection.LIBRARY)
                    },
                )
            },
        )

        if (!showPlayerSheet && !navigationState.isSettingsFlowActive) {
            MiniPlayer(
                playbackController = appContainer.playbackController,
                onOpenPlayer = {
                    showPlayerSheet = true
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        bottom = FloatingToolbarOverlayHeight +
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FloatingNavigationItem(
    selected: Boolean,
    iconRes: Int,
    labelRes: Int,
    shouldHideLabel: Boolean,
    onClick: () -> Unit,
) {
    val labelWidth by animateDpAsState(
        targetValue = if (selected && !shouldHideLabel) 80.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "floatingNavigationLabelWidth",
    )
    val hapticFeedback = LocalHapticFeedback.current
    val colorScheme = MaterialTheme.colorScheme

    IconButton(
        onClick = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
            onClick()
        },
        modifier = Modifier
            .width(48.dp + labelWidth)
            .height(48.dp)
            .semantics { this.selected = selected },
        colors = if (selected) {
            IconButtonDefaults.filledIconButtonColors(
                contentColor = colorScheme.onPrimaryContainer,
                containerColor = colorScheme.primaryContainer,
            )
        } else {
            IconButtonDefaults.iconButtonColors(
                contentColor = colorScheme.onSurfaceVariant,
            )
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = stringResource(labelRes),
                modifier = Modifier.size(24.dp),
            )

            if (selected && !shouldHideLabel) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.labelLarge,
                    color = colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 80.dp),
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

private fun navigationContentKey(key: NavKey): String =
    when (key) {
        HomeDestination -> "home"
        HomeRecentlyAddedDestination -> "home:recently-added"
        SearchDestination -> "search"
        LibraryDestination -> "library"
        is ArtistDestination -> "${key.owner.contentKeyPrefix()}:artist:${key.artistName}"
        is SingleDestination -> "${key.owner.contentKeyPrefix()}:single:${key.releaseKey}"
        is AlbumDestination -> "${key.owner.contentKeyPrefix()}:album:${key.releaseKey}"
        is GenreDestination -> "${key.owner.contentKeyPrefix()}:genre:${key.genreKey}"
        is ArtistGenreDestination ->
            "${key.owner.contentKeyPrefix()}:artist-genre:${key.artistName}:${key.genreKey}"
        is SettingsDestination -> "${key.owner.contentKeyPrefix()}:settings"
        is FolderFilterDestination -> "${key.owner.contentKeyPrefix()}:folder-filter"
        is AboutDestination -> "${key.owner.contentKeyPrefix()}:about"
        else -> "unknown:${key::class.qualifiedName}:${key}"
    }

private fun TopLevelSection.contentKeyPrefix(): String =
    when (this) {
        TopLevelSection.HOME -> "home"
        TopLevelSection.SEARCH -> "search"
        TopLevelSection.LIBRARY -> "library"
    }

private fun NavKey?.isSettingsFlowDestination(): Boolean =
    when (this) {
        is SettingsDestination,
        is FolderFilterDestination,
        is AboutDestination -> true
        else -> false
    }

private fun destinationSection(destinationContentKey: Any?): TopLevelSection? {
    val contentKey = destinationContentKey as? String ?: return null

    return when {
        contentKey == "home" || contentKey.startsWith("home:") -> TopLevelSection.HOME
        contentKey == "search" || contentKey.startsWith("search:") -> TopLevelSection.SEARCH
        contentKey == "library" || contentKey.startsWith("library:") -> TopLevelSection.LIBRARY
        else -> null
    }
}

private fun isTopLevelDestination(destinationContentKey: Any?): Boolean =
    destinationContentKey == "home" ||
        destinationContentKey == "search" ||
        destinationContentKey == "library"

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

private class RoviaNavigationState(
    val backStacks: Map<NavKey, NavBackStack<NavKey>>,
    private val topLevelSectionState: MutableState<TopLevelSection>,
) {
    val topLevelSection: TopLevelSection
        get() = topLevelSectionState.value

    val topLevelRoute: NavKey
        get() = topLevelSection.asDestination()

    private val activeBackStack: NavBackStack<NavKey>?
        get() = backStacks[topLevelRoute]

    val isSettingsFlowActive: Boolean
        get() = activeBackStack?.lastOrNull().isSettingsFlowDestination() == true

    fun isTopLevelSectionSelected(section: TopLevelSection): Boolean =
        topLevelSection == section && !isSettingsFlowActive

    fun selectTopLevel(section: TopLevelSection) {
        clearSettingsFlowFromStack(topLevelSection)
        topLevelSectionState.value = section
    }

    private fun clearSettingsFlowFromStack(section: TopLevelSection) {
        val stack = backStacks[section.asDestination()] ?: return

        while (stack.size > 1 && stack.lastOrNull().isSettingsFlowDestination()) {
            stack.removeLastOrNull()
        }
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
            topLevelSectionState.value = TopLevelSection.HOME
        }
    }

    @Composable
    fun toDecoratedEntries(
        entryProvider: (NavKey) -> NavEntry<NavKey>,
    ): List<NavEntry<NavKey>> {
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