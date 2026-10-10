
package com.rovia.music

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.rovia.music.core.library.FolderBrowserRepository
import com.rovia.music.core.library.FolderFilterRepository
import com.rovia.music.core.library.FolderScannerRepository
import com.rovia.music.core.library.LyricsRepository
import com.rovia.music.core.library.MusicRepository
import com.rovia.music.core.playback.PlaybackController
import com.rovia.music.feature.home.HomeRoute
import com.rovia.music.feature.home.RecentCollectionRoute
import com.rovia.music.feature.home.RecentCollectionType
import com.rovia.music.feature.library.LibraryRoute
import com.rovia.music.feature.search.SearchRoute
import com.rovia.music.feature.settings.AboutScreen
import com.rovia.music.feature.settings.FolderFilterScreen
import com.rovia.music.feature.settings.SettingsScreen
import kotlin.math.roundToInt

internal val NavigationBarHeight =
    80.dp

internal val MiniPlayerNavigationSpacing =
    8.dp

private val NavigationBarFullRadius =
    40.dp

private val NavigationBarStackedTopRadius =
    20.dp

private val NavigationBarBottomRadius =
    40.dp

private val NavigationBarHorizontalPadding =
    14.dp

private val NavigationBarBottomPadding =
    20.dp

internal val NavigationBarBottomInset =
    NavigationBarHeight +
        NavigationBarBottomPadding

private val NavigationBarItemPadding =
    6.dp

@Composable
fun MainNavigation(
    musicRepository: MusicRepository,
    folderBrowserRepository: FolderBrowserRepository,
    folderFilterRepository: FolderFilterRepository,
    folderScannerRepository: FolderScannerRepository,
    playbackController: PlaybackController,
    lyricsRepository: LyricsRepository,
) {
    val backStack =
        rememberNavBackStack(Home)

    val currentDestination =
        backStack.lastOrNull()

    val playbackState by
        playbackController.playbackState
            .collectAsStateWithLifecycle()

    val excludedFolders by
        folderFilterRepository.excludedFolders
            .collectAsStateWithLifecycle()

    val motionScheme =
        MaterialTheme.motionScheme

    val density =
        LocalDensity.current

    val eightDpInPixels =
        with(density) {
            8.dp.toPx()
        }

    var playerProgress by remember {
        mutableFloatStateOf(0f)
    }

    val showBottomChrome =
        when (currentDestination) {
            Settings,
            FolderFilter,
            About -> false

            else -> true
        }

    Box(
        modifier =
            Modifier.fillMaxSize(),
    ) {
        NavDisplay(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            backStack =
                backStack,
            onBack = {
                /*
                 * Home, Search, and Library are top-level destinations.
                 *
                 * Detail destinations are added to the back stack
                 * and removed when Back is pressed.
                 */
                if (backStack.size > 1) {
                    backStack.removeLastOrNull()
                }
            },
            /*
             * Forward navigation is completely instantaneous.
             */
            transitionSpec = {
                EnterTransition.None togetherWith
                    ExitTransition.None
            },
            /*
             * Normal/programmatic Back is also completely instantaneous.
             */
            popTransitionSpec = {
                EnterTransition.None togetherWith
                    ExitTransition.None
            },
            /*
             * Predictive Back keeps the existing surface motion.
             */
            predictivePopTransitionSpec = {
                EnterTransition.None togetherWith
                    (
                        scaleOut(
                            targetScale =
                                0.9f,
                            transformOrigin =
                                TransformOrigin.Center,
                            animationSpec =
                                motionScheme
                                    .defaultSpatialSpec(),
                        ) +
                            slideOutHorizontally(
                                targetOffsetX = { fullWidth ->
                                    (
                                        (
                                            fullWidth * 0.05f
                                        ) -
                                            eightDpInPixels
                                    )
                                        .coerceAtLeast(
                                            0f,
                                        )
                                        .roundToInt()
                                },
                                animationSpec =
                                    motionScheme
                                        .defaultSpatialSpec(),
                            )
                    )
            },
            entryProvider =
                entryProvider {
                    entry<Home> {
                        NavigationDestinationSurface {
                            HomeRoute(
                                musicRepository =
                                    musicRepository,
                                playbackController =
                                    playbackController,
                                onOpenSettings = {
                                    backStack.add(
                                        Settings,
                                    )
                                },
                                onOpenArtist = { artistName ->
                                    backStack.add(
                                        ArtistDetail(
                                            artistName = artistName,
                                        ),
                                    )
                                },
                                onOpenAlbum = {
                                        albumTitle,
                                        artistName,
                                    ->
                                    backStack.add(
                                        AlbumDetail(
                                            albumTitle = albumTitle,
                                            artistName = artistName,
                                        ),
                                    )
                                },
                                onOpenGenre = { genreName ->
                                    backStack.add(
                                        GenreDetail(
                                            genreName = genreName,
                                        ),
                                    )
                                },
                            )
                        }
                    }

                    entry<ArtistDetail> { key ->
                        NavigationDestinationSurface {
                            RecentCollectionRoute(
                                musicRepository =
                                    musicRepository,
                                playbackController =
                                    playbackController,
                                collectionType =
                                    RecentCollectionType.ARTIST,
                                collectionName =
                                    key.artistName,
                                onBack = {
                                    backStack.removeLastOrNull()
                                },
                            )
                        }
                    }

                    entry<AlbumDetail> { key ->
                        NavigationDestinationSurface {
                            RecentCollectionRoute(
                                musicRepository =
                                    musicRepository,
                                playbackController =
                                    playbackController,
                                collectionType =
                                    RecentCollectionType.ALBUM,
                                collectionName =
                                    key.albumTitle,
                                albumArtist =
                                    key.artistName,
                                onBack = {
                                    backStack.removeLastOrNull()
                                },
                            )
                        }
                    }

                    entry<GenreDetail> { key ->
                        NavigationDestinationSurface {
                            RecentCollectionRoute(
                                musicRepository =
                                    musicRepository,
                                playbackController =
                                    playbackController,
                                collectionType =
                                    RecentCollectionType.GENRE,
                                collectionName =
                                    key.genreName,
                                onBack = {
                                    backStack.removeLastOrNull()
                                },
                            )
                        }
                    }

                    entry<Search> {
                        NavigationDestinationSurface {
                            SearchRoute(
                                musicRepository =
                                    musicRepository,
                                playbackController =
                                    playbackController,
                                onOpenSettings = {
                                    backStack.add(
                                        Settings,
                                    )
                                },
                            )
                        }
                    }

                    entry<Library> {
                        NavigationDestinationSurface {
                            LibraryRoute(
                                musicRepository =
                                    musicRepository,
                                folderBrowserRepository =
                                    folderBrowserRepository,
                                playbackController =
                                    playbackController,
                                isPlayerOpen =
                                    playerProgress > 0f,
                                onOpenSettings = {
                                    backStack.add(
                                        Settings,
                                    )
                                },
                            )
                        }
                    }

                    entry<Settings> {
                        NavigationDestinationSurface {
                            SettingsScreen(
                                onBack = {
                                    backStack.removeLastOrNull()
                                },
                                onFolderFilterClick = {
                                    backStack.add(
                                        FolderFilter,
                                    )
                                },
                                onAboutClick = {
                                    backStack.add(
                                        About,
                                    )
                                },
                            )
                        }
                    }

                    entry<FolderFilter> {
                        NavigationDestinationSurface {
                            FolderFilterScreen(
                                folderFilterRepository =
                                    folderFilterRepository,
                                folderScannerRepository =
                                    folderScannerRepository,
                                filteredFolderCount =
                                    excludedFolders.size,
                                onBack = {
                                    backStack.removeLastOrNull()
                                },
                            )
                        }
                    }

                    entry<About> {
                        NavigationDestinationSurface {
                            AboutScreen(
                                onBack = {
                                    backStack.removeLastOrNull()
                                },
                            )
                        }
                    }
                },
        )

        UnifiedPlayerSheet(
            playbackState =
                playbackState,
            playbackController =
                playbackController,
            lyricsRepository =
                lyricsRepository,
            musicRepository =
                musicRepository,
            onProgressChanged = { progress ->
                playerProgress =
                    progress
            },
            showBottomChrome =
                showBottomChrome,
            navigationBar = {
                RoviaNavigationBar(
                    currentDestination =
                        currentDestination,
                    backStack =
                        backStack,
                    playerProgress =
                        playerProgress,
                    hasTrack =
                        playbackState.currentTrack != null,
                )
            },
            modifier =
                Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun NavigationDestinationSurface(
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    color =
                        MaterialTheme
                            .colorScheme
                            .background,
                ),
    ) {
        content()
    }
}

@Composable
private fun RoviaNavigationBar(
    currentDestination: NavKey?,
    backStack: MutableList<NavKey>,
    playerProgress: Float,
    hasTrack: Boolean,
) {
    val cardColors =
        CardDefaults.cardColors()

    val navigationBarItemColors =
        NavigationBarItemDefaults.colors(
            selectedIconColor =
                MaterialTheme
                    .colorScheme
                    .onPrimary,
            selectedTextColor =
                MaterialTheme
                    .colorScheme
                    .onSurface,
            indicatorColor =
                MaterialTheme
                    .colorScheme
                    .primary,
            unselectedIconColor =
                MaterialTheme
                    .colorScheme
                    .onSecondaryContainer,
            unselectedTextColor =
                MaterialTheme
                    .colorScheme
                    .onSecondaryContainer,
        )

    val miniPlayerVisibility =
        if (hasTrack) {
            (
                1f -
                    playerProgress
            ).coerceIn(
                0f,
                1f,
            )
        } else {
            0f
        }

    val navigationBarTopRadius =
        NavigationBarFullRadius -
            (
                (
                    NavigationBarFullRadius -
                        NavigationBarStackedTopRadius
                ) *
                    miniPlayerVisibility
            )

    val navigationBarShape =
        RoundedCornerShape(
            topStart =
                navigationBarTopRadius,
            topEnd =
                navigationBarTopRadius,
            bottomStart =
                NavigationBarBottomRadius,
            bottomEnd =
                NavigationBarBottomRadius,
        )

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    start =
                        NavigationBarHorizontalPadding,
                    top = 0.dp,
                    end =
                        NavigationBarHorizontalPadding,
                    bottom =
                        NavigationBarBottomPadding,
                )
                .height(
                    NavigationBarHeight,
                )
                .background(
                    color =
                        cardColors.containerColor,
                    shape =
                        navigationBarShape,
                ),
    ) {
        NavigationBar(
            modifier =
                Modifier.fillMaxSize(),
            containerColor =
                Color.Transparent,
            contentColor =
                cardColors.contentColor,
            tonalElevation =
                0.dp,
            windowInsets =
                WindowInsets(
                    left = 0,
                    top = 0,
                    right = 0,
                    bottom = 0,
                ),
        ) {
            NavigationBarItem(
                modifier =
                    Modifier.padding(
                        horizontal =
                            NavigationBarItemPadding,
                    ),
                selected =
                    currentDestination == Home,
                onClick = {
                    navigateToTopLevel(
                        backStack =
                            backStack,
                        destination =
                            Home,
                    )
                },
                icon = {
                    Icon(
                        imageVector =
                            Icons.Filled.Home,
                        contentDescription =
                            stringResource(
                                R.string.nav_home,
                            ),
                    )
                },
                label = {
                    Text(
                        stringResource(
                            R.string.nav_home,
                        ),
                    )
                },
                colors =
                    navigationBarItemColors,
            )

            NavigationBarItem(
                modifier =
                    Modifier.padding(
                        horizontal =
                            NavigationBarItemPadding,
                    ),
                selected =
                    currentDestination == Search,
                onClick = {
                    navigateToTopLevel(
                        backStack =
                            backStack,
                        destination =
                            Search,
                    )
                },
                icon = {
                    Icon(
                        imageVector =
                            Icons.Filled.Search,
                        contentDescription =
                            stringResource(
                                R.string.nav_search,
                            ),
                    )
                },
                label = {
                    Text(
                        stringResource(
                            R.string.nav_search,
                        ),
                    )
                },
                colors =
                    navigationBarItemColors,
            )

            NavigationBarItem(
                modifier =
                    Modifier.padding(
                        horizontal =
                            NavigationBarItemPadding,
                    ),
                selected =
                    currentDestination == Library,
                onClick = {
                    navigateToTopLevel(
                        backStack =
                            backStack,
                        destination =
                            Library,
                    )
                },
                icon = {
                    Icon(
                        imageVector =
                            Icons.AutoMirrored
                                .Filled.List,
                        contentDescription =
                            stringResource(
                                R.string.nav_library,
                            ),
                    )
                },
                label = {
                    Text(
                        stringResource(
                            R.string.nav_library,
                        ),
                    )
                },
                colors =
                    navigationBarItemColors,
            )
        }
    }
}

private fun navigateToTopLevel(
    backStack: MutableList<NavKey>,
    destination: NavKey,
) {
    /*
     * Home, Search, and Library are siblings.
     *
     * Switching between them replaces the current top-level
     * destination instead of pushing a new back-stack entry.
     */
    if (
        backStack.lastOrNull() ==
            destination
    ) {
        return
    }

    backStack.clear()
    backStack.add(
        destination,
    )
}
