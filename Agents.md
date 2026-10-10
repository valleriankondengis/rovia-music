# Rovia

Rovia is a native Android offline music player focused on local audio playback, explicit modular architecture, Material 3 Expressive UI, Android dynamic color, Media3/ExoPlayer playback, MediaSession background playback, Navigation 3, Predictive Back, embedded synced lyrics, local search, and system-language localization.

This file is the **current engineering contract** for Rovia.

It is written so a human developer or AI coding agent can understand the application before changing it.

> **READ THIS FILE BEFORE MODIFYING ROVIA.**
>
> Rovia is an existing application, not a blank template. Preserve existing behavior unless the developer explicitly requests a behavior change.
>
> When uncertain, do less. Do not invent infrastructure or features.

---

# 1. PRODUCT IDENTITY

| Item | Current value |
|---|---|
| Application | `Rovia` |
| Package | `com.rovia.music` |
| Version name | `0.1.0` |
| Version code | `1` |
| Minimum Android | Android 16 / API 36 |
| Target Android | Android 16 / API 36 |
| Compile SDK | API 37 |
| Language | Kotlin only |
| UI | Jetpack Compose |
| Design system | Material 3 Expressive |
| Color system | Android system dynamic color / Monet |
| Navigation | AndroidX Navigation 3 |
| Playback | AndroidX Media3 + ExoPlayer |
| Background playback | `MediaSessionService` |
| Persistence | Room 3 over Android SQLite for app-owned persistent state |
| Network | None |
| Image loading | Android platform APIs only |
| DI | Manual `AppContainer` |

---

# 2. CURRENT FEATURE SET

The current application contains these real features:

- Runtime `READ_MEDIA_AUDIO` permission handling.
- Local audio discovery through Android `MediaStore`.
- A Room-backed music catalog synchronized with MediaStore, used as the read source for Home, Library, Search, and active-track metadata.
- Per-volume MediaStore version/generation tracking to skip unchanged catalog scans and perform incremental synchronization when possible.
- Separate bounded enrichment of selected embedded metadata; catalog listing does not open every audio file with `MediaMetadataRetriever`.
- Home screen.
- Recently Added section, limited to 10 real tracks.
- Persistent Recent Play history keyed by distinct content URI, with no hard 10-record database cap; Home previews at most 10 recent entries.
- Home Recent Play collections grouped by Artist, Album, and Genre, with dedicated detail pages and real track lists.
- Library screen containing the complete local audio collection.
- Library All Songs / Folders mode selector using Material 3 Expressive `ButtonGroup`.
- Context-aware Library sorting through `LibrarySortBottomSheet`, with options filtered by the current browse mode.
- Artist, Album, and Genre collection rows with type-appropriate real artwork.
- MediaStore-derived folder browsing without triggering filesystem/media rescans.
- Tapable folder navigation path / breadcrumb for moving between root and nested folders.
- Folder parent navigation and explicit root/all-songs state handling.
- Folder Filter UI with native Material 3 controls and an add action, with persistent exclusion selections.
- Search screen with realtime local search and fuzzy matching.
- Real playback through Media3/ExoPlayer.
- In-memory playback queue.
- Play, pause, resume, seek, previous, and next.
- Global MiniPlayer while a track is active.
- Unified expandable/morphing Player sheet implemented as a single physical Player surface.
- MiniPlayer content morphs into the full Player surface through continuous sheet progress rather than a
  separate navigation destination or shared-element transition.
- Swipe up from the MiniPlayer area to open Player.
- Swipe down from Player to collapse Player.
- MiniPlayer downward dismissal gesture that stops playback and clears the queue when the dismissal threshold is reached.
- Player expansion progress survives Activity recreation/portrait-landscape rotation through saved Compose state.
- Android Predictive Back for the Player sheet.
- Navigation 3 Predictive Back for normal destinations, with no forward or normal-pop destination animation.
- MediaSession background playback and system media controls.
- Real MediaStore artwork loading through Android thumbnails.
- Audio technical information such as sample rate, bitrate, and MIME-derived format when metadata exists.
- Additional real track metadata fields such as release date, label, copyright, and release type when the
  source exposes them; missing values remain missing and are not fabricated.
- Embedded local lyrics parsing.
- Line-synced and word-synced lyrics.
- Automatic lyric scrolling and active-word highlighting.
- Active lyric-line scaling with adaptive handling for long lyric lines.
- Fullscreen lyrics mode that replaces the Player artwork area with the lyric viewer.
- Fullscreen lyrics mode remains active while Previous/Next changes tracks; only the lyric content is reloaded.
- Dedicated fullscreen lyrics MiniPlayer with independent playback controls and a separate seekbar surface.
- Fullscreen lyrics seekbar shows elapsed playback time on the left and uses a fully rounded pill container.
- Fullscreen lyrics MiniPlayer uses the same fully rounded pill geometry for visual consistency with its seekbar.
- Fullscreen lyrics bottom spacing matches the NavigationBar's 20dp bottom padding.
- Player artwork/lyrics toggle.
- Portrait and landscape Player layouts.
- Material 3 Expressive controls and motion.
- System dynamic color / Monet.
- English and Indonesian localization through Android resources.
- Settings destination, Folder Filter screen, and About destination.

Features that are not implemented must not be added as fake UI.

---

# 3. NON-NEGOTIABLE RULES

These are architectural rules, not suggestions.

## 3.1 No fake data

Never insert:

- demo songs;
- sample artists;
- example albums;
- fake Recent Play entries;
- fake artwork;
- fake lyrics;
- fake audio-quality labels.

If data does not exist, show the actual empty/unavailable state.

## 3.2 Application database and synchronized music catalog

Rovia uses **Room 3 over Android SQLite** for its persistent application state and its local, queryable
music catalog. Database implementation details are isolated in `:data:database`.

Current database tables:

- `music_tracks`: the synchronized local catalog used by Home, Library, Search, and track metadata observation;
- `media_store_sync_state`: per-volume MediaStore version/generation checkpoints;
- `recent_plays`: persistent Recent Play records keyed by distinct content URI, with no hard 10-record cap;
- `excluded_folders`: persisted Folder Filter selections.

**MediaStore remains authoritative for which local media files exist and for indexed MediaStore values.**
Room intentionally stores a synchronized catalog snapshot so the app does not have to query and parse the
entire library on every screen/startup. Room is a local index/cache for catalog reads, not the source of
truth for the underlying files. Synchronization reconciles Room against MediaStore.

Do not introduce another persistence/database technology for these existing responsibilities. Keep Room,
DAOs, entities, SQLite, and concrete database repositories inside `:data:database`; feature modules use
Core repository APIs rather than accessing Room/SQLite directly.

Do not add `DataStore` or `SharedPreferences` as an alternative for data that belongs in the existing
structured database.

Current database stack:

```text
Room 3
    ↓
AndroidSQLiteDriver
    ↓
Android framework SQLite
```

## 3.3 No network

Do not add networking, remote metadata, online lyrics, streaming, accounts, or cloud sync.

## 3.4 No alternate audio engine

Do not add FFmpeg, FFprobe, another decoder framework, or a second playback engine.

Media3 + ExoPlayer is the playback foundation.

## 3.5 No DI framework

Do not add Hilt, Dagger, Koin, or a global service locator.

## 3.6 No custom design system

Use the configured Material 3 Expressive APIs and `MaterialTheme` tokens.

Do not replace them with hardcoded colors, arbitrary shadows, fake expressive components, or a custom color palette.

## 3.7 Do not turn Player into a normal navigation destination

Player is currently implemented as a layered `UnifiedPlayerSheet`, not as a regular Navigation 3 destination.

`NavigationKeys.kt` still contains a `Player` key, but the active Player presentation does not push that key into the main Navigation 3 back stack.

Do not infer that Player should be converted into a normal destination.

## 3.8 Preserve Predictive Back

Predictive Back is a deliberate interaction feature. Do not replace it with a normal `BackHandler` while implementing unrelated changes.

## 3.9 Preserve module boundaries

Feature modules should depend on API/model layers and shared UI, not concrete Media3 or MediaStore implementations.

---

# 4. TOOLCHAIN BASELINE

Current version catalog baseline, checked against `gradle/libs.versions.toml`:

```text
Android Gradle Plugin       : 9.4.0
Kotlin                      : 2.4.20
AndroidX Core KTX           : 1.19.1
AndroidX Activity Compose   : 1.13.0
AndroidX Lifecycle          : 2.11.0
AndroidX Navigation 3       : 1.2.0
Compose BOM                 : 2026.09.00
Material 3                  : 1.5.0-alpha29
AndroidX Graphics Shapes    : 1.1.0
AndroidX Media3              : 1.11.1
Kotlin Coroutines            : 1.10.2
AndroidX Test Core           : 1.7.0
AndroidX Test Ext JUnit      : 1.3.0
AndroidX Test Runner          : 1.7.0
AndroidX Test Espresso Core  : 3.7.0
JUnit                       : 4.13.2
KSP                         : 2.3.12
Room 3                      : 3.0.3
AndroidX SQLite              : 2.7.1
JDK                         : 17
Java source/target           : 17
compileSdk                  : 37
minSdk                      : 36
targetSdk                   : 36
```

The library and plugin versions above mirror the version keys currently declared in `gradle/libs.versions.toml`. The SDK levels and JDK/source compatibility values are build configuration and toolchain settings, not version-catalog keys.

Source of versions:

```text
gradle/libs.versions.toml
```

Module build files should use version-catalog aliases instead of hardcoded library versions.

---

# 5. PROJECT STRUCTURE

Rovia is now a Kotlin-only Android project at the source-tree level.

All application source files use:

```text
src/main/kotlin/
```

All Android resources remain under:

```text
src/main/res/
```

There is currently no Kotlin source under `src/main/java/`, and there are no `.java` source files in
the application modules.

Current source layout:

```text
Rovia/
│
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── kotlin/com/rovia/music/
│       │   ├── AppContainer.kt
│       │   ├── MainActivity.kt
│       │   ├── Navigation.kt
│       │   ├── NavigationKeys.kt
│       │   ├── RoviaApplication.kt
│       │   ├── UnifiedPlayerSheet.kt
│       │   └── theme/
│       │       └── Theme.kt
│       └── res/
│
├── core/
│   ├── model/
│   │   └── src/main/kotlin/com/rovia/music/core/model/
│   │       ├── Lyrics.kt
│   │       ├── MusicFolder.kt
│   │       ├── PlaybackState.kt
│   │       └── Track.kt
│   │
│   ├── library-api/
│   │   └── src/main/kotlin/com/rovia/music/core/library/
│   │       ├── FolderBrowserRepository.kt
│   │       ├── FolderFilterRepository.kt
│   │       ├── FolderScannerRepository.kt
│   │       ├── LyricsRepository.kt
│   │       └── MusicRepository.kt
│   │
│   ├── playback-api/
│   │   └── src/main/kotlin/com/rovia/music/core/playback/
│   │       ├── PlaybackController.kt
│   │       └── RecentPlayRepository.kt
│   │
│   └── ui/
│       └── src/main/kotlin/com/rovia/music/core/ui/component/
│           ├── AlbumArtwork.kt
│           ├── MiniPlayer.kt
│           └── TrackRow.kt
│
├── data/
│   ├── database/
│   │   ├── build.gradle.kts
│   │   ├── schemas/
│   │   │   └── com.rovia.music.data.database.RoviaDatabase/
│   │   │       └── 1.json
│   │   ├── src/main/kotlin/com/rovia/music/data/database/
│   │   │   ├── RoviaDatabase.kt
│   │   │   ├── RoviaDatabaseProvider.kt
│   │   │   ├── dao/
│   │   │   │   ├── ExcludedFolderDao.kt
│   │   │   │   ├── MediaStoreSyncStateDao.kt
│   │   │   │   ├── MusicTrackDao.kt
│   │   │   │   └── RecentPlayDao.kt
│   │   │   ├── entity/
│   │   │   │   ├── ExcludedFolderEntity.kt
│   │   │   │   ├── MediaStoreSyncStateEntity.kt
│   │   │   │   ├── MusicTrackEntity.kt
│   │   │   │   └── RecentPlayEntity.kt
│   │   │   ├── mapper/
│   │   │   │   └── RecentPlayEntityMapper.kt
│   │   │   └── repository/
│   │   │       ├── RoomFolderFilterRepository.kt
│   │   │       ├── RoomMusicCatalogRepository.kt
│   │   │       └── RoomRecentPlayRepository.kt
│   │   └── src/androidTest/kotlin/com/rovia/music/data/database/
│   │       └── RoviaDatabaseMigrationTest.kt
│   │
│   └── media-store/
│       └── src/main/kotlin/com/rovia/music/data/media/store/
│           ├── EmbeddedLyricsRepository.kt
│           ├── EmbeddedMetadataEnrichmentProcessor.kt
│           ├── EmbeddedMetadataSignature.kt
│           ├── MediaCatalogSyncCoordinator.kt
│           ├── MediaStoreCatalogDataSource.kt
│           ├── MediaStoreFolderBrowserRepository.kt
│           ├── MediaStoreFolderScannerRepository.kt
│           └── MediaStoreMusicRepository.kt  # legacy repository; not AppContainer's catalog source
│
├── playback/
│   └── media3/
│       └── src/main/kotlin/com/rovia/music/playback/media3/
│           ├── Media3PlaybackController.kt
│           └── RoviaMediaSessionService.kt
│
└── feature/
    ├── home/
    │   └── src/main/kotlin/com/rovia/music/feature/home/
    │       ├── HomeRoute.kt
    │       ├── HomeScreen.kt
    │       ├── HomeUiState.kt
    │       ├── HomeViewModel.kt
    │       ├── HomeViewModelFactory.kt
    │       ├── RecentCollectionRoute.kt
    │       ├── RecentCollectionScreen.kt
    │       └── RecentPlayCollection.kt
    │
    ├── search/
    │   └── src/main/kotlin/com/rovia/music/feature/search/
    │       ├── SearchRoute.kt
    │       ├── SearchScreen.kt
    │       ├── SearchUiState.kt
    │       ├── SearchViewModel.kt
    │       └── SearchViewModelFactory.kt
    │
    ├── library/
    │   └── src/main/kotlin/com/rovia/music/feature/library/
    │       ├── LibraryRoute.kt
    │       ├── LibraryScreen.kt
    │       ├── LibrarySort.kt
    │       ├── LibrarySortBottomSheet.kt
    │       ├── LibraryUiState.kt
    │       ├── LibraryViewModel.kt
    │       └── LibraryViewModelFactory.kt
    │
    ├── player/
    │   └── src/main/kotlin/com/rovia/music/feature/player/
    │       ├── LyricViewer.kt
    │       ├── PlayerActionButtonGroup.kt
    │       ├── PlayerArtwork.kt
    │       ├── PlayerLyric.kt
    │       ├── PlayerLandscapeContent.kt
    │       ├── PlayerPlaybackControls.kt
    │       ├── PlayerPortraitContent.kt
    │       ├── PlayerLyricsFullscreenContent.kt
    │       ├── PlayerLyricsMiniPlayer.kt
    │       ├── PlayerRoute.kt
    │       ├── PlayerScreen.kt
    │       ├── PlayerSeekBar.kt
    │       ├── PlayerTrackInfo.kt
    │       ├── PlayerUiState.kt
    │       └── PlayerViewModel.kt
    │
    └── settings/
        └── src/main/kotlin/com/rovia/music/feature/settings/
            ├── FolderFilterScreen.kt
            └── SettingsScreen.kt
```

Source-tree invariants:

```text
Kotlin source      -> src/main/kotlin/
Android resources  -> src/main/res/
Java source        -> none in the current Rovia modules
```

Do not reintroduce `src/main/java` for Kotlin files. Do not move files back to the old layout
without an explicit architectural reason.

No empty future modules should be introduced.

---

# 6. MODULE OWNERSHIP

## `:app`

Owns application composition:

- Activity;
- runtime permission entry point;
- theme entry point;
- Navigation 3 root;
- top-level navigation bar;
- `UnifiedPlayerSheet`;
- manual dependency wiring.

## `:core:model`

Owns domain models:

```text
Track
PlaybackState
SyncedLyrics
LyricLine
LyricWord
```

## `:core:library-api`

Owns library abstractions:

```text
MusicRepository
FolderBrowserRepository
FolderFilterRepository
FolderScannerRepository
LyricsRepository
```

## `:core:playback-api`

Owns:

```text
PlaybackController
RecentPlayRepository
```

## `:core:ui`

Owns reusable presentation components and local shared vector icons:

```text
AlbumArtwork
TrackRow
MiniPlayer
```

## `:data:database`

Owns:

- Room 3 database configuration;
- SQLite driver configuration;
- Room entities;
- Room DAOs;
- Room repository implementations;
- database schema export;
- application-owned persistent state.

Current persistent tables:

```text
music_tracks
media_store_sync_state
recent_plays
excluded_folders
```

`recent_plays` retains one most-recent record per distinct content URI. It is not capped at 10 rows; the Home screen's 10-item preview is a UI-only limit.

This module is the only module that should depend directly on Room 3 and SQLite implementation APIs.

## `:data:media-store`

Owns:

- MediaStore queries;
- MediaStore-derived folder discovery;
- local artwork access;
- audio metadata extraction;
- embedded lyric parsing;
- MediaStore-backed folder scanning.

Folder Filter persistence does not belong here. The MediaStore module consumes the `FolderFilterRepository`
API and applies the persisted exclusions when querying MediaStore.

Folder filtering and folder browsing operate on MediaStore-derived data. They must not trigger an
independent filesystem scan or force a media rescan just to update the Library UI.

## `:playback:media3`

Owns:

- Media3 controller;
- ExoPlayer service;
- MediaSession;
- queue implementation;
- playback position updates;
- Recent Play event emission through `RecentPlayRepository`.

The playback module does not know about Room or SQLite.

## `:feature:home`

Owns Home UI and Home ViewModel state, including the 10-entry Recent Play preview, Artist/Album/Genre grouping derived from persisted Recent Play records, collection detail navigation, and collection detail track lists. The collection UI must not fabricate metadata or silently switch its source to the full library catalog.

## `:feature:search`

Owns Search UI and local fuzzy-search logic.

## `:feature:library`

Owns full Library UI and Library ViewModel state, including:

- All Songs / Folders mode switching;
- root folder listing;
- nested folder browsing;
- folder breadcrumb/navigation path;
- parent-folder navigation;
- folder-derived track lists;
- context-aware sorting and its mode-specific option sheet;
- Artist / Album / Genre collection-list artwork and sorting where those browse contexts are displayed;
- track sorting inside a selected collection;
- MiniPlayer-aware content spacing.

## `:feature:player`

Owns Player UI, Player ViewModel, lyrics toggle/state, and lyric viewer.

## `:feature:settings`

Owns Settings UI and Folder Filter UI.

Current Settings/Folder Filter responsibilities include:

- Settings top app bar;
- back navigation;
- Folder Filter screen;
- folder list presentation;
- add-folder action;
- Material 3 Expressive spacing/shape contracts;
- persistent Folder Filter selection through the Core `FolderFilterRepository` API.

Do not infer that a broader persistent settings system exists beyond the implemented UI and explicitly implemented persistent data.

---

# 7. DEPENDENCY DIRECTION

The intended direction is:

```text
                         :app
                           │
           ┌───────────────┼─────────────────────────┐
           │               │                         │
       feature:*      data:media-store        playback:media3
           │               │                         │
           │         core:library-api               │
           │               │                       │
           │               └──────────┐            │
           │                          │            │
           │                   :data:database       │
           │                          │            │
           └────────────────── core APIs/models ───┘
                              │
                   ┌──────────┴──────────┐
                   │                     │
             library-api           playback-api
                   │                     │
                   └──────── model ──────┘
```

The important architectural rule is that UI feature modules do not construct ExoPlayer or perform raw MediaStore queries.

---

# 8. APPLICATION STARTUP

`MainActivity` currently:

1. calls `enableEdgeToEdge()`;
2. obtains the `RoviaApplication` container;
3. enters `RoviaTheme`;
4. checks `READ_MEDIA_AUDIO`;
5. requests the permission when it is missing;
6. renders the real permission state when access is unavailable;
7. creates `MainNavigation` after the permission is granted.

The app does not fake a library when permission is unavailable.

---

# 9. AUDIO PERMISSION

Current manifest permission:

```xml
<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
```

The application also declares the foreground playback permissions required by the current Media3 service setup:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
```

Do not add image/video permissions or unrelated permissions without a real implementation requirement.

---

# 10. APP CONTAINER

`AppContainer` manually wires the application's shared repositories and controllers. The current catalog
read path is Room-backed; `MediaStoreMusicRepository` is not the `MusicRepository` instance used by the app.

Current wiring:

```text
MusicRepository
    -> RoomMusicCatalogRepository
    -> MusicTrackDao / music_tracks

MediaStoreCatalogDataSource
    -> MediaStore volume/version/generation and audio queries

MediaCatalogSyncCoordinator
    -> synchronizes MediaStore changes into music_tracks
    -> stores per-volume checkpoints in media_store_sync_state

EmbeddedMetadataEnrichmentProcessor
    -> reads selected embedded metadata for pending tracks in bounded batches
    -> updates music_tracks and matching recent_plays metadata

FolderScannerRepository
    -> MediaStoreFolderScannerRepository

FolderFilterRepository
    -> RoomFolderFilterRepository

LyricsRepository
    -> EmbeddedLyricsRepository

RecentPlayRepository
    -> RoomRecentPlayRepository

PlaybackController
    -> Media3PlaybackController
```

`MainActivity` requests catalog synchronization after audio permission is available and again when the
activity resumes. `AppContainer` guards against launching duplicate active catalog-sync jobs. Catalog
synchronization happens off the UI thread. Embedded metadata enrichment is separate from the MediaStore
catalog query and uses bounded batches (currently 24 tracks per batch).

The `:app` module owns manual dependency wiring. It passes the existing `MusicRepository` instance to
Home, Library, Search, and the Player route. Do not construct a second catalog repository inside a feature.

## 10.1 DATABASE AND MEDIASTORE CATALOG ARCHITECTURE

Rovia uses Room 3 over the Android framework SQLite driver, stored in `rovia.db`, as both:

- persistent storage for application-owned state; and
- the reactive local catalog/index for MediaStore audio tracks.

Current tables and keys:

### `music_tracks`

Stores the synchronized local music catalog. The composite primary key is `(volume_name, track_id)` because
MediaStore IDs must be interpreted together with their volume. It holds the real `Track` fields needed by the
UI, plus synchronization/enrichment data such as `generation_modified`, `embedded_metadata_signature`,
`embedded_metadata_status`, and the last enrichment timestamp.

The UI-facing catalog repository maps these entities to `Track`. Its `observeAllTracks()`,
`observeRecentlyAdded(limit)`, and `observeTrackByUri(uri)` Flows emit catalog updates from Room. The URI
lookup is used to observe metadata for the currently active player track without querying the entire library
for that individual observation.

### `media_store_sync_state`

Stores the MediaStore version and last successfully synchronized generation/checkpoint per volume. Do not
advance a checkpoint before all changes represented by it have been handled successfully.

### `recent_plays`

Stores the persistent Recent Play recency list without a hard 10-row cap. The primary key is `uri`;
`track_id` is additional metadata, not the unique key, because track IDs can overlap across volumes. Replaying a URI updates that URI's record and last-played timestamp instead of creating a duplicate row for the same URI. Stored track metadata includes real values when available, such as release date, label, copyright, and release type. When embedded release date/copyright metadata is enriched later, the matching Recent Play snapshot is updated by URI. Missing tags remain null; do not fabricate values. The Home preview limit is applied in UI state, not by deleting database history.

### `excluded_folders`

Stores the user's excluded relative folder paths. Folder Filter selections remain application-owned
persistent state; folder discovery and the underlying audio files remain MediaStore responsibilities.

### MediaStore synchronization contract

`MediaStoreCatalogDataSource` discovers available external volumes and reads their MediaStore version,
generation, audio rows, or lightweight IDs. It does not write to Room and does not use
`MediaMetadataRetriever` during catalog listing.

`MediaCatalogSyncCoordinator` follows these rules:

1. On first synchronization, a missing checkpoint, a changed MediaStore version, or a generation rollback,
   perform a full scan for that volume.
2. If the stored MediaStore version matches and generation advanced, query rows whose
   `GENERATION_MODIFIED` is newer than the last successful checkpoint.
3. If version and generation are unchanged, skip the audio catalog query for that volume.
4. Reconcile track IDs so entries removed from MediaStore are removed from the Room catalog.
5. Treat an unavailable volume as unavailable, not as an empty volume; do not delete its cached catalog just
   because it is absent from the current mounted-volume list.
6. Persist a successful checkpoint only after the corresponding synchronization/reconciliation succeeds.

MediaStore remains authoritative for underlying files, volume availability, content URIs, paths, and
MediaStore-indexed metadata. Room intentionally stores a synchronized catalog of the complete available local
library so normal UI reads do not need to scan and parse every file again. Do not describe this catalog as
“application-only state” or prohibit storing the synchronized track catalog in Room; that policy is obsolete.

### Embedded metadata enrichment

`EmbeddedMetadataEnrichmentProcessor` reads selected tags (`releaseDate` and copyright) independently from
catalog scanning. It processes pending tracks in bounded batches (currently 24) on `Dispatchers.IO`. It records
a source signature and processing status so a track that has already been processed is not reopened on every
startup. A changed MediaStore source signature can mark its embedded metadata pending again. An absent tag is
a valid missing value; a failed read is tracked separately from a successful read with no tag.

The metadata signature is based on MediaStore/source metadata fields; it is not a cryptographic checksum of
the entire audio file. Do not call it a content hash.

### Active player metadata

`PlayerViewModel` observes the active playback URI through `MusicRepository.observeTrackByUri()`. When Room
emits a newer catalog `Track` for the same URI, the UI state uses that refreshed track for presentation while
keeping playback position, play/pause state, duration, repeat, and shuffle sourced from
`PlaybackController.playbackState`. This UI refresh must not restart playback or rebuild the Media3 queue.
The playback module remains unaware of Room/SQLite.

`artworkUri` currently uses the MediaStore content URI for the track. Embedded metadata enrichment does not
update `artwork_uri`, and the artwork loader remains based on Android `ContentResolver.loadThumbnail()`.
Do not add cache-busting or a separate artwork cache as part of unrelated metadata changes.

### Schema version and current development policy

The current `RoviaDatabase` schema version remains `1`, with its current exported schema at:

```text
data/database/schemas/com.rovia.music.data.database.RoviaDatabase/1.json
```

Keep that export aligned with the current `RoviaDatabase` entities and schema. During the current active
Rovia development cycle, do not increment the schema version or add migrations/automigrations merely to
preserve local development data unless the developer explicitly asks for a migration. Clearing the app's local
data or reinstalling is an accepted development reset after an incompatible schema change.

This development convenience is not a production data-loss policy. Before a release where existing user data
must survive an upgrade, explicitly establish the released schema baseline, increment the version for a real
schema evolution, preserve released schema exports, and implement/test the required migration path. Do not add
`fallbackToDestructiveMigration()` as a production recovery strategy.

Keep schema tests aligned with the actual current table set. Migration tests must use a dedicated test database;
they must not delete or alter the developer's real `rovia.db`.

---

# 11. MATERIAL 3 EXPRESSIVE

Rovia uses actual Material 3 Expressive APIs rather than imitating them.

The theme uses:

```kotlin
MaterialExpressiveTheme(
    colorScheme = colorScheme,
    motionScheme = MotionScheme.expressive(),
    content = content,
)
```

The codebase uses Expressive components such as:

```text
ButtonGroup
ButtonGroupDefaults
FilledIconButton
IconButtonDefaults
LoadingIndicator
MaterialTheme.motionScheme
```

The Player and MiniPlayer use these APIs for playback controls.

Do not replace them with a custom button implementation just to simplify code.

---

# 12. DYNAMIC MONET COLOR

Rovia uses Android system dynamic color:

```kotlin
dynamicDarkColorScheme(context)
dynamicLightColorScheme(context)
```

Light/dark selection follows the system theme:

```kotlin
isSystemInDarkTheme()
```

Application colors must come from:

```text
MaterialTheme.colorScheme
```

Do not introduce:

```text
#FF...
custom brand primary
custom dark palette
custom light palette
hardcoded surface colors
```

unless the developer explicitly changes the product-wide theme strategy.

## 12.1 PURE-BLACK ROM COMPATIBILITY FOR MATERIAL SURFACES

Some Android custom ROMs can aggressively collapse or override selected Material surface roles
such as `surfaceContainer*` into pure black. Rovia must not assume that every explicit
`MaterialTheme.colorScheme.surfaceContainer*` override will render identically on every ROM.

The current verified compatibility rule is:

```text
Material 3 theme
    ↓
MaterialTheme.colorScheme
    ↓
official component default colors
    ↓
ROM-safe resolved UI color
```

When a Material component already provides a correct themed default, prefer the component's
official default color resolution instead of forcing a specific `surfaceContainer*` role.

A concrete verified example is the main `NavigationBar`.

The original implementation explicitly used:

```kotlin
containerColor = MaterialTheme.colorScheme.surfaceContainer
contentColor = MaterialTheme.colorScheme.onSurface
```

On the developer's custom ROM, that surface was forced to pure black.

The verified implementation instead resolves the NavigationBar container/content colors from
the same official Material Card color defaults used by the working MiniPlayer:

```kotlin
val cardColors = CardDefaults.cardColors()

NavigationBar(
    containerColor = cardColors.containerColor,
    contentColor = cardColors.contentColor,
)
```

This preserves dynamic theming while avoiding the ROM-specific pure-black override observed with
the explicit `surfaceContainer` role.

Do not blindly copy this mechanism to every component. First inspect the working component and
use the official Material 3 default color API for that component where available.

Important:

- Do not hardcode black, white, or custom RGB colors to work around a ROM.
- Do not abandon Monet/dynamic color.
- Do not create a second theme system for ROM compatibility.
- Do not replace Material 3 components with custom containers merely because one surface role
  is rendered incorrectly.
- Prefer official component defaults and semantic `MaterialTheme.colorScheme` roles.
- Verify the resulting color behavior on the real Android device.

## 12.2 THEMED ICONS AND MONET COLOR APPLICATION

Rovia's in-app icons must remain theme-aware.

The project uses local Android Vector Drawable resources for icons and applies them through
Compose `Icon`. Icon appearance must come from Material semantic colors rather than fixed
application colors.

Preferred pattern:

```kotlin
Icon(
    painter = painterResource(R.drawable.ic_example),
    tint = MaterialTheme.colorScheme.onPrimaryContainer,
)
```

or allow an official Material component to provide the appropriate `LocalContentColor` when the
icon is rendered inside that component.

For NavigationBar items, use semantic roles such as:

```kotlin
selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer
unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
indicatorColor = MaterialTheme.colorScheme.secondaryContainer
```

For icon containers, use semantic container/content pairs such as:

```text
primaryContainer + onPrimaryContainer
secondaryContainer + onSecondaryContainer
surfaceContainer* + onSurface/onSurfaceVariant
```

Do not:

```text
hardcode icon colors
use pure white/black as the application-wide icon tint
sample album-art colors for global icon theming
bundle a third-party icon pack
replace themed vector icons with emoji
```

The goal is:

```text
Android system color / Monet
        ↓
MaterialExpressiveTheme
        ↓
MaterialTheme.colorScheme
        ↓
component semantic colors
        ↓
themed icons and controls
```

The launcher themed-icon requirement remains separate from in-app icon tinting. The adaptive launcher
icon must continue to provide its monochrome layer, while in-app icons follow Material theme colors.

## 12.3 MATERIAL 3 EXPRESSIVE COMPONENT DEFAULTS ARE PART OF THE THEME CONTRACT

Material 3 Expressive is not only a visual style. Its official components and defaults are part of
Rovia's runtime color and motion behavior.

When an existing component already renders correctly with the configured theme, preserve its
official default color/elevation/shape behavior before introducing explicit overrides.

Examples already used successfully in Rovia include:

```text
ElevatedCard / CardDefaults
ButtonGroup
FilledIconButton
IconButtonDefaults
NavigationBar / NavigationBarItem
MaterialTheme.motionScheme
```

For a new UI component:

1. Prefer the official Material 3 / Material 3 Expressive component.
2. Start with its official defaults.
3. Add explicit semantic colors only when the design actually requires a different role.
4. Avoid overriding a surface color merely to make the component visually resemble another
   component.
5. Test on the real Android device, including custom ROM builds with aggressive pure-black
   surface behavior.

This prevents an AI agent from "fixing" one ROM quirk by introducing hardcoded colors that break
Monet or other system themes.

---

# MATERIAL 3 EXPRESSIVE COMPONENT DECISION MATRIX

This section is a strict UI implementation contract for Rovia.

The purpose is to prevent a developer or AI coding agent from replacing Material 3 Expressive
components with visually similar custom Compose implementations, freezing native shape-morph
behavior into static `RoundedCornerShape` values, or creating custom shapes where an official
Material 3 Expressive API already exists.

The decision order is mandatory:

```text
Native Material 3 / Material 3 Expressive component exists
        ↓
USE THE NATIVE COMPONENT
        ↓
Does the component expose an official shape / morph API?
        ↓
YES ──→ USE THE OFFICIAL SHAPE / MORPH API
        │
        └──→ Preserve its interaction-driven shape behavior
        ↓
NO
        ↓
USE THE COMPONENT'S OFFICIAL DEFAULT SHAPE
        ↓
Only when the visual or interaction requirement cannot be represented
by the native component:
        ↓
Use a documented custom Compose shape / Canvas implementation
```

Do not reverse this decision order.

## 12.4.1 Native Component Must Be Used

When Material 3 or Material 3 Expressive already provides the required UI primitive,
Rovia must use the official component instead of recreating the component with generic
`Row`, `Box`, `Surface`, `Card`, or custom drawing.

Examples:

```text
TopAppBar
ButtonGroup
ListItem
FilledIconButton
FilledIconToggleButton
NavigationBar
NavigationBarItem
SearchBar / SearchBarDefaults.InputField
LoadingIndicator
Card / ElevatedCard
```

The native component is the source of truth for:

```text
layout semantics
interaction semantics
content color behavior
container color behavior
elevation behavior
accessibility semantics
shape defaults
Expressive motion behavior
```

A custom wrapper is allowed only when it preserves the native component behavior and adds
application-specific composition around it. Do not reimplement the internal Material component
behavior.

## 12.4.2 Native Shape / Morph API Must Be Prioritized

When an official Material 3 Expressive component exposes a shape or shape-morph API, that API
must be preferred over a manually assigned static shape.

Examples currently relevant to Rovia include:

```kotlin
IconButtonDefaults.shapes()
IconButtonDefaults.toggleableShapes()
ListItemDefaults.shapes()
ButtonDefaults.shapes()
```

and the shape behavior provided by:

```text
ButtonGroup
ButtonGroupDefaults
official Material 3 Expressive buttons
official Material 3 list items
```

The important rule is:

```text
Native expressive shape API
        >
static RoundedCornerShape(...)
        >
custom shape implementation
```

for components that already expose the native behavior.

The shape API must remain attached to the component so that the component can preserve
its intended state-dependent behavior such as:

```text
pressed
selected
toggled
focused
hovered
disabled
connected button-group positions
```

Do not collapse an expressive component to one static corner value merely because the static
shape looks visually similar in one screenshot.

## 12.4.3 When Explicit dp Shape Values Are Allowed

Explicit `dp` values are allowed when the geometry is part of Rovia's documented layout contract
and the value does not replace an existing native Material shape/morph mechanism.

Valid examples include:

```text
MiniPlayer outer container:
topStart    = 32dp
topEnd      = 32dp
bottomStart = 20dp
bottomEnd   = 20dp

Library / Settings / Folder Filter header:
horizontal outer padding = 4dp

Expressive group spacing:
8dp

Album artwork:
56dp where the current component contract requires a 56dp artwork slot

MiniPlayer / NavigationBar layering:
NavigationBar height = 80dp
MiniPlayerNavigationSpacing = 8dp
```

These values are geometry contracts for Rovia. They should not be casually changed to
"something that looks better" without updating `Agents.md` and testing the affected screens.

Explicit dp geometry is therefore allowed for:

```text
spacing
padding
insets
component dimensions
artwork dimensions
container geometry
custom sheet geometry
custom visual components
```

Explicit dp geometry must NOT be used to override a native expressive shape/morph API when the
native API already provides the required behavior.

## 12.4.4 When a Custom Shape Is Allowed

A custom `Shape`, `RoundedCornerShape`, `Canvas`, or equivalent Compose implementation is allowed
only when the requested visual behavior is genuinely application-specific and cannot be represented
cleanly by an official Material 3 / Material 3 Expressive component.

Current Rovia custom-valid areas include:

```text
MiniPlayer outer asymmetric container shape
UnifiedPlayerSheet custom sheet geometry
WavySeekBar / Canvas visualization
AlbumArtwork-specific artwork presentation
LyricViewer-specific visual layout and synchronization behavior
other explicitly documented media-specific visualizations
```

These custom implementations are valid because they represent application-specific visual or
interaction behavior rather than replacing a standard Material control.

For every new custom shape, the developer or AI agent must be able to answer:

```text
Which official Material 3 component was considered?
Why does it not provide the required geometry/interaction?
Why is a custom shape necessary?
Which dp values define the resulting geometry?
```

If that justification cannot be given, prefer the native Material 3 / Expressive component.

## 12.4.5 Current Rovia Native Material 3 Expressive Components

The following components are considered native and must remain native unless there is an explicit
architecture decision:

```text
TopAppBar
ButtonGroup
ButtonGroupDefaults
FilledIconButton
FilledIconToggleButton
IconButtonDefaults
ListItem
ListItemDefaults
ToggleButton
NavigationBar
NavigationBarItem
NavigationBarItemDefaults
LoadingIndicator
SearchBar / SearchBarDefaults.InputField
Card / ElevatedCard
CardDefaults
MaterialExpressiveTheme
MaterialTheme.motionScheme
```

Current Rovia usage includes:

```text
Library header
    → TopAppBar

Settings header
    → TopAppBar

Folder Filter header
    → TopAppBar

All Songs / Folders selector
    → ButtonGroup

Library folder breadcrumb/path
    → ButtonGroup / connected toggle-style items

Track rows
    → ListItem

Folder rows
    → ListItem

Player top actions
    → ButtonGroup + FilledIconButton / FilledIconToggleButton

Player playback controls
    → ButtonGroup + expressive icon buttons

MiniPlayer playback controls
    → ButtonGroup + FilledIconButton

Navigation
    → NavigationBar / NavigationBarItem

Search input
    → SearchBar / SearchBarDefaults.InputField

Loading states
    → LoadingIndicator
```

Do not replace these with visually equivalent hand-built controls unless the native component has
been verified as insufficient for the exact requirement.

## 12.4.6 Current Rovia Custom-Valid Components

The following areas are intentionally custom and must not be "normalized" into generic Material
components merely for code consistency:

```text
AlbumArtwork
    → application-specific media artwork component

MiniPlayer outer container
    → asymmetric custom container geometry

UnifiedPlayerSheet
    → custom sheet, gesture, predictive-back, and progress system

WavySeekBar
    → custom Canvas-based seek visualization and touch interaction

LyricViewer
    → timestamp-aware lyric visualization and synchronized scrolling/highlighting
```

The distinction is:

```text
STANDARD APP CONTROL
    → Material 3 Expressive native component

APPLICATION-SPECIFIC VISUALIZATION / INTERACTION
    → custom Compose implementation when justified
```

Do not confuse a custom implementation with a custom design system. Rovia may have a few
application-specific components while still remaining fully based on Material 3 Expressive.

## 12.4.7 DO / DON'T

### DO

Use the native component:

```kotlin
TopAppBar(
    navigationIcon = { ... },
    actions = { ... },
)
```

Use the native shape behavior:

```kotlin
FilledIconButton(
    shapes = IconButtonDefaults.shapes(),
    onClick = { ... },
)
```

Use toggle-aware expressive shapes where appropriate:

```kotlin
FilledIconToggleButton(
    shapes = IconButtonDefaults.toggleableShapes(),
    checked = checked,
    onCheckedChange = { ... },
)
```

Use native list-item shapes:

```kotlin
ListItem(
    shapes = ListItemDefaults.shapes(),
    ...
)
```

Use the Material ButtonGroup instead of manually drawing connected controls:

```kotlin
ButtonGroup(
    ...
)
```

Keep documented geometry values explicit when they are part of the Rovia layout contract:

```text
4dp
8dp
20dp
32dp
56dp
80dp
```

### DON'T

Do not replace a native expressive button with:

```kotlin
FilledIconButton(
    shape = RoundedCornerShape(28.dp),
    ...
)
```

when `IconButtonDefaults.shapes()` or another official shape API provides the intended behavior.

Do not turn a native `ListItem` into:

```kotlin
Row(
    modifier = Modifier
        .clip(RoundedCornerShape(...))
        .clickable { ... }
)
```

when the required behavior is already represented by `ListItem`.

Do not manually connect buttons with arbitrary negative spacing, clipping, or overlapping shapes
when `ButtonGroup` provides the intended Expressive grouping behavior.

Do not create a custom `TopAppBar` by stacking a `Row` over the screen just to reproduce the
appearance of the official app bar.

Do not replace native Expressive motion with arbitrary static geometry simply because a screenshot
looks acceptable.

## 12.4.8 Never Replace Native Morphing With Static RoundedCornerShape

This is a specific Rovia regression rule.

Never perform a change like:

```text
BEFORE
IconButtonDefaults.shapes()
        ↓
native Expressive shape behavior

AFTER
RoundedCornerShape(28.dp)
        ↓
static shape
```

only because the static shape appears visually close.

The two implementations are not considered equivalent.

The native implementation may encode state-dependent shape behavior that a static
`RoundedCornerShape` does not reproduce.

The same rule applies to:

```text
IconButtonDefaults.toggleableShapes()
ListItemDefaults.shapes()
ButtonDefaults.shapes()
ButtonGroup-connected shape behavior
other official Material 3 Expressive shape APIs
```

When an AI agent sees an existing official shape/morph API, it must preserve it unless the developer
explicitly requests a behavior change.

## 12.4.9 Shape Change Checklist

Before changing a shape, answer all of the following:

```text
[ ] Is this UI element already a Material 3 / Expressive component?
[ ] Does the component already provide a shape API?
[ ] Does that API provide state-dependent or morphing behavior?
[ ] Is the requested change actually a geometry requirement or only a visual preference?
[ ] Can the requirement be satisfied by the official API?
[ ] If not, why is a custom shape necessary?
[ ] Are all explicit dp values documented?
[ ] Does the change preserve accessibility and interaction semantics?
[ ] Has the affected screen been tested on the real Android device?
[ ] Has ./compiledebug been run after the change?
```

If the answer to the native-component question is `yes` and the requested behavior is covered by
the official API, do not introduce a custom shape.

## 12.4.10 AI Agent Priority Order

For UI changes, an AI coding agent must follow this exact priority:

```text
1. Preserve the existing Rovia implementation.
2. Prefer official Material 3 Expressive components.
3. Prefer official Material 3 Expressive shape/morph APIs.
4. Prefer official component defaults before explicit overrides.
5. Use explicit dp values only for documented geometry contracts.
6. Use custom Shape / Canvas only for justified application-specific UI.
7. Document any new geometry or shape contract in `Agents.md`.
8. Compile immediately after the change.
9. Test the affected interaction on the real device.
10. Do not "simplify" native Expressive behavior into static shapes.
```

The desired result is:

```text
Material 3 Expressive
        +
native component defaults
        +
native shape/morph behavior
        +
explicit documented geometry
        +
small number of justified custom visualizations
```

This decision matrix is mandatory for future UI changes. Its purpose is to prevent visual regressions,
loss of Expressive state behavior, inconsistent padding/gaps, and accidental reimplementation of
official Material 3 components.

---

# 13. MATERIAL 3 EXPRESSIVE MOTION

The application uses:

```kotlin
MaterialTheme.motionScheme
```

for existing Material 3 Expressive interaction motion and for explicitly documented Player and
Predictive Back behavior.

Native Material 3 Expressive component motion remains part of the UI contract when it is provided by
the official component API or is explicitly documented by the affected feature.

Do not introduce arbitrary application-wide animations merely to make a screen feel more animated.

For Navigation 3 destination changes, the stricter navigation motion contract in Sections 16 and 17
takes precedence.

Animation changes are behavior changes and must not be introduced during unrelated feature work.

---

# 14. NAVIGATION 3 DESTINATIONS

The active Navigation 3 destinations are:

```text
Home
Search
Library
Settings
FolderFilter
About
RecentCollection detail (Artist / Album / Genre)
```

Recent collection detail is opened from the corresponding Artist, Album, or Genre collection on Home. It is a child page, not a fourth bottom-navigation tab. Back returns to the originating Home context. The currently supported top-level bottom-navigation siblings remain Home, Search, and Library.

The bottom navigation exposes exactly these top-level siblings:

```text
Home
Search
Library
```

`Home`, `Search`, and `Library` are peers. None of them is the parent of another.

Settings is opened from the Settings button in Home, Search, and Library.

Folder Filter and About are child destinations of Settings.

Player is not pushed onto this Navigation 3 back stack by the current implementation. The Player remains
the layered `UnifiedPlayerSheet`.

The intended hierarchy is:

```text
Top-level siblings:
    Home
    Search
    Library

Child navigation:
    Home
        └── RecentCollection detail
                ├── Artist
                ├── Album
                └── Genre

    Settings
        ├── FolderFilter
        └── About
```

---

# 15. TOP-LEVEL NAVIGATION BEHAVIOR

Top-level switching must not build an ever-growing back stack.

`Home`, `Search`, and `Library` are mutually exclusive top-level destinations. Switching between them
replaces the current top-level destination:

```text
Home
  ↕
Search
  ↕
Library
```

The current implementation is:

```kotlin
if (backStack.lastOrNull() == destination) {
    return
}

backStack.clear()
backStack.add(destination)
```

This means:

```text
Home → Search
Search → Home
Library → Home
Home → Library
```

are top-level switches, not Back-stack relationships.

Predictive Back must not be triggered simply because the user switches between Home, Search, and Library.

Back is only meaningful when an actual child destination exists above the active top-level destination.

Do not reintroduce a permanent Home root below Search or Library unless the navigation architecture is
explicitly redesigned.

---

# 16. NAVIGATION TRANSITIONS

Rovia intentionally has no Navigation 3 destination-enter animation and no normal/programmatic
destination-pop animation.

The authoritative `Navigation.kt` implementation is:

```kotlin
transitionSpec = {
    EnterTransition.None togetherWith
        ExitTransition.None
}

popTransitionSpec = {
    EnterTransition.None togetherWith
        ExitTransition.None
}
```

Therefore:

```text
Forward navigation
    → instantaneous

Normal/programmatic Back
    → instantaneous

Predictive Back gesture
    → gesture-driven surface motion only
```

Do not add the following to `transitionSpec` or `popTransitionSpec`:

```text
fadeIn
fadeOut
scaleIn
scaleOut
slideIn
slideOut
shared-element transitions
container transforms
custom page transitions
```

Do not add Navigation 3 per-entry transition metadata merely to give a newly added destination an
entrance animation.

This absence of normal destination animation is intentional product behavior.

---

# 17. PREDICTIVE BACK FOR NAVIGATION

Predictive Back is the only Navigation 3 destination transition that intentionally animates a normal
destination surface.

The current `predictivePopTransitionSpec` is:

```text
scaleOut targetScale = 0.90f
transformOrigin     = Center
horizontal movement = approximately 5% of full width minus 8dp
fade                = none
```

The horizontal movement is calculated as:

```kotlin
targetOffsetX = { fullWidth ->
    (
        (fullWidth * 0.05f) - eightDpInPixels
    )
        .coerceAtLeast(0f)
        .roundToInt()
}
```

The resulting interaction is:

```text
edge back gesture
      ↓
current destination surface progressively:
    - scales toward 90%
    - moves toward the right
    - does not fade
      ↓
gesture completed
      ↓
destination is popped
```

The distinction between normal Back and Predictive Back is mandatory:

```text
popTransitionSpec
    → no animation

predictivePopTransitionSpec
    → scale to 90%
    → move right
    → no fade
```

Do not add fade to Predictive Back.

Do not change the 90% target scale or the documented rightward-motion model during unrelated feature
work.

---

# 17.1 NAVIGATION DESTINATION SURFACE CONTRACT

Every active Navigation 3 destination is wrapped in the shared:

```text
NavigationDestinationSurface
```

The current surface implementation provides a full-screen opaque background using:

```kotlin
Box(
    modifier =
        Modifier
            .fillMaxSize()
            .background(
                MaterialTheme
                    .colorScheme
                    .background,
            ),
)
```

This wrapper is currently applied to:

```text
Home
Search
Library
Settings
FolderFilter
About
RecentCollection detail (Artist / Album / Genre)
```

This is required for Predictive Back so the destination is animated as a complete surface. Without an
opaque destination surface, transparent regions can reveal the page below and make the current screen
appear to lose its surface while only text, icons, or controls remain visible.

When adding a new Navigation 3 destination, preserve this surface contract.

Do not assume that the screen's child content already provides an opaque full-screen background.

---

# 17.2 NAVIGATION.KT IMPLEMENTATION CONTRACT

The authoritative navigation file is:

```text
app/src/main/kotlin/com/rovia/music/Navigation.kt
```

Future navigation changes must preserve:

```text
Home / Search / Library
    = top-level siblings

RecentCollection detail (Artist / Album / Genre)
    = child route opened from Home's recent collection sections

Settings
    = child of the active top-level destination

FolderFilter / About
    = children of Settings

Forward destination transition
    = none

Normal/programmatic pop transition
    = none

Predictive Back destination transition
    = scale to 90% + move right + no fade

Every destination
    = full opaque NavigationDestinationSurface

Player
    = UnifiedPlayerSheet, not a Navigation 3 destination
```

When adding a new navigation destination:

```text
1. Add the destination key.
2. Register the destination in Navigation 3.
3. Wrap it in NavigationDestinationSurface.
4. Define the parent/back-stack behavior explicitly (recent collection details are child pages opened from Home).
5. Keep forward navigation instantaneous.
5. Keep normal/programmatic Back instantaneous.
6. Preserve the existing Predictive Back motion.
7. Make the destination hierarchy explicit.
8. Do not create a new navigation transition system.
```

When adding a new top-level destination, do not automatically place it below Home in the Back stack.
Follow the sibling model used by Home, Search, and Library unless the product architecture explicitly
defines a different hierarchy.

---

# 17.3 NAVIGATION BACK SEMANTICS

The active `onBack` behavior is intentionally:

```kotlin
onBack = {
    if (backStack.size > 1) {
        backStack.removeLastOrNull()
    }
}
```

The current top-level destination itself is therefore not treated as a child that should be popped.

For example:

```text
Library
```

does not become a child of Home merely because the user previously selected Home.

But:

```text
Library
   ↓
Settings
```

does create a child destination that Back can remove.

Likewise:

```text
Library
   ↓
Settings
   ↓
FolderFilter
```

Back removes FolderFilter first, then Settings.

Recent collection details follow the same child-page principle:

```text
Home
   ↓
RecentCollection detail (Artist / Album / Genre)
```

Back returns from the collection detail to Home; the collection is not a bottom-navigation sibling.

Do not add special-case logic that makes Home behave differently from Search or Library.

---

# 18. UNIFIED PLAYER SHEET: CORE ARCHITECTURE

`app/src/main/kotlin/com/rovia/music/UnifiedPlayerSheet.kt` is the central Player interaction layer.

It combines:

```text
Full Player
MiniPlayer
NavigationBar
Predictive Back
Vertical sheet gestures
Playback stop/dismiss behavior
```

The application still uses a root `Box` with the navigation content underneath and
`UnifiedPlayerSheet` above it, but the Player/MiniPlayer relationship is now implemented as a
continuous morphing surface rather than two independently stacked cards.

Conceptually:

```text
Root Box
│
├── Navigation content
│
└── UnifiedPlayerSheet
    ├── single physical Player surface
    │   ├── MiniPlayerContent at low progress
    │   └── full Player content at high progress
    │
    └── NavigationBar chrome layer
```

The physical Player surface changes its geometry continuously as `progress` changes. The MiniPlayer is
therefore not a separate navigation destination and the full Player does not rely on a
`SharedTransitionLayout`.

The NavigationBar remains a separate bottom-chrome layer so it can be independently hidden when the
Player reaches its fully expanded state.

---

# 19. PLAYER SHEET PROGRESS

The full Player sheet has normalized progress:

```text
0.0 = collapsed
1.0 = fully expanded
```

This state drives:

- Player alpha;
- Player scale;
- Player translation;
- Player corner interpolation;
- MiniPlayer alpha;
- NavigationBar alpha/translation;
- predictive back mapping.

Keep this state as the single source of truth for the sheet expansion itself.

The current `progress` state is stored with `rememberSaveable` so an Activity recreation caused by
portrait/landscape rotation does not reset an open Player back to the collapsed MiniPlayer state.

After restoration, `UnifiedPlayerSheet` propagates the restored progress through its
`onProgressChanged` callback so the rest of the application immediately observes the restored
Player-open state.

---

# 20. FULL PLAYER VISUAL MODEL

The current full Player layer uses approximately:

```text
alpha = progress
scale = 0.96 + 0.04 * progress
translationY = 48dp * (1 - progress)
```

The transform origin is bottom-center:

```text
pivotFractionX = 0.5
pivotFractionY = 1.0
```

Top corner rounding is derived from:

```text
cornerProgress = 1 - progress
```

Values are clamped to prevent invalid negative corner sizes when motion animations overshoot.

Do not remove those clamps.

---

# 20.1. MORPHING PLAYER SURFACE GEOMETRY

The current `UnifiedPlayerSheet` uses one physical `Surface` for the MiniPlayer-to-Player transition.

The surface morphs continuously with the same normalized `progress`:

```text
progress = 0.0
    -> collapsed MiniPlayer geometry

progress = 1.0
    -> full-screen Player geometry
```

Current geometry contract:

```text
collapsed height
    = 80dp

horizontal outer padding
    = approximately 14dp at the collapsed state
    = 0dp at the fully expanded state

bottom corner radius
    = approximately 20dp at the collapsed state
    = 0dp at the fully expanded state

top corner radius
    = approximately 32dp at the collapsed state
    = 0dp at the fully expanded state
```

The surface's container color also morphs from the working Material Card container treatment toward
`MaterialTheme.colorScheme.background` as the Player opens.

The morph is intentionally implemented without `SharedTransitionLayout`. The Player surface itself
changes size, padding, radius, and content state.

Do not split this back into unrelated MiniPlayer and full-Player surfaces unless the architecture is
explicitly redesigned.

---

# 21. PLAYER SWIPE DOWN

When Player is open, downward drag reduces Player progress.

The current gesture only reacts to downward movement for this path.

On release, current behavior is:

```text
progress >= 0.5
    -> expand()

progress < 0.5
    -> collapse()
```

The Player therefore behaves as a continuous sheet during the drag and a settled sheet after the finger is released.

---

# 22. MINIPLAYER SWIPE UP

The MiniPlayer has a dedicated gesture layer for upward expansion.

Swiping upward adjusts the same Player-sheet `progress` used by the expanded Player.

When release crosses the current 0.5 decision threshold:

```text
expand()
```

otherwise:

```text
collapse()
```

Do not move the gesture to the NavigationBar layer.

---

# 23. MINIPLAYER SWIPE DOWN / DISMISS

The current MiniPlayer implementation also supports downward dismissal.

It uses:

```text
miniPlayerDismissProgress
miniPlayerGestureDirection
```

The gesture is intentionally separate from the Player expansion progress.

Downward drag has resistance using the MiniPlayer height.

The current resistance calculation includes a factor of approximately:

```text
1.35
```

Dismissal is committed after the current threshold:

```text
miniPlayerDismissProgress >= 0.45
```

On commit:

```text
stopMiniPlayer()
    -> settle MiniPlayer away
    -> playbackController.stopAndClearQueue()
```

Therefore this is an actual playback-stop interaction, not only a visual hide operation.

Do not change this semantic accidentally.

---

# 24. PLAYER PREDICTIVE BACK

`UnifiedPlayerSheet` owns:

```kotlin
PredictiveBackHandler(
    enabled = progress > 0f,
)
```

During the predictive back gesture:

```text
backEvent.progress = 0.0 -> 1.0
```

is mapped to:

```text
sheet progress = 1.0 -> 0.0
```

Conceptually:

```kotlin
nextProgress = 1f - backEvent.progress
```

The Player therefore progressively collapses with the back gesture.

If the gesture is completed, the sheet collapses.

If the gesture is cancelled, the Player is settled back to the expanded state.

This behavior must remain synchronized with the visual sheet transition.

---

# 25. MINIPLAYER MUST NOT OWN BACK

`MiniPlayer.kt` is a reusable presentation component.

It does not own the application's Predictive Back behavior.

Back is coordinated by `UnifiedPlayerSheet`.

`MiniPlayer` receives only playback/content callbacks:

```text
onPrevious
onPlayPause
onNext
```

Opening/expanding the Player is coordinated by `UnifiedPlayerSheet`, not by a click-navigation callback
inside the reusable `MiniPlayer` component itself.

Do not add a second back callback or an `onOpenPlayer` callback to the shared `MiniPlayer`.

---

# 26. MINI PLAYER VISUAL DESIGN

Current MiniPlayer uses Material 3 Expressive building blocks:

```text
ElevatedCard
AlbumArtwork
ButtonGroup
FilledIconButton
IconButtonDefaults
animateWidth()
```

The main playback controls are:

```text
Previous
Play/Pause
Next
```

Center Play/Pause uses the primary container color.

Previous/Next use surface-container colors.

Do not add custom borders or arbitrary drop shadows.

The global `:core:ui` MiniPlayer keeps its documented asymmetric outer geometry because it participates
in the main application's sheet transition.

Fullscreen lyrics uses a separate `:feature:player` component named `PlayerLyricsMiniPlayer`. It is
intentionally not the global MiniPlayer component because the fullscreen lyrics interaction needs its
own independent seekbar and direct playback controls.

`PlayerLyricsMiniPlayer` currently uses:

```text
seekbar container
    = full pill

lyrics MiniPlayer container
    = full pill

outer vertical gap
    = 8dp

seekbar inner horizontal padding
    = 16dp

seekbar elapsed-time ↔ seekbar gap
    = 10dp
```

The dedicated fullscreen lyrics controls preserve the same Material theme and playback actions but do
not expose an `onOpenPlayer` interaction.

---

# 27. NAVIGATION BAR + MINIPLAYER LAYERING

The MiniPlayer and NavigationBar are independent layers.

Current navigation bar height is:

```text
80dp
```

The navigation bar's bottom padding is:

```text
20dp
```

The MiniPlayer-to-NavigationBar spacing contract is:

```text
8dp
```

The NavigationBar is kept at a higher `zIndex` than the MiniPlayer.

When the Player reaches the fully expanded state, the NavigationBar is not merely made transparent:
its composable layer is removed from composition. This is important because an invisible NavigationBar
could otherwise continue intercepting touch events intended for the fullscreen Player controls.

The navigation bar contract remains:

```text
NavigationBarHeight
    = 80dp

NavigationBarBottomPadding
    = 20dp

NavigationBarBottomInset
    = 100dp

MiniPlayerNavigationSpacing
    = 8dp
```

Fullscreen lyrics uses the same `20dp` bottom spacing as the NavigationBar so its bottom MiniPlayer
does not visually float at an unrelated distance from the screen edge.

Do not merge the NavigationBar and Player surface into one parent card.

---

# 28. PLAYER SCREEN OWNERSHIP

`feature/player` contains:

```text
PlayerRoute
PlayerScreen
PlayerPortraitContent
PlayerLandscapeContent
PlayerArtwork
PlayerLyric
PlayerActionButtonGroup
PlayerPlaybackControls
PlayerSeekBar
PlayerTrackInfo
PlayerLyricsFullscreenContent
PlayerLyricsMiniPlayer
PlayerViewModel
PlayerUiState
LyricViewer
```

`PlayerRoute` connects application APIs to the UI.

`PlayerScreen` is the orchestration layer: it owns transient seek interaction state, derives
orientation, and selects the portrait or landscape composition. It does not render the detailed
Player controls itself.

`PlayerPortraitContent` and `PlayerLandscapeContent` own the orientation-specific visual layout.

`PlayerArtwork` owns Player artwork presentation. `PlayerLyric` owns embedded-lyrics presentation and fallback state.

`PlayerActionButtonGroup` owns the top Player actions and the track-information menu/sheet entry point.

`PlayerPlaybackControls` owns previous/play-pause/next and repeat/shuffle controls.

`PlayerSeekBar` owns the custom interactive wavy seek bar.

`PlayerTrackInfo` owns technical metadata, seek-time labels, and the Player-local formatting helpers.

`PlayerLyricsFullscreenContent` owns the fullscreen lyrics layout, including the dedicated bottom
fullscreen playback area.

`PlayerLyricsMiniPlayer` owns the fullscreen lyrics seekbar + MiniPlayer composition. Its seekbar state
is local to that fullscreen component and is forwarded to the same playback seek callback.

`PlayerViewModel` coordinates playback state and lyrics state.

`LyricViewer` renders timestamp-aware lyrics.

The feature module does not construct ExoPlayer.

---

# 29. PLAYER PORTRAIT / LANDSCAPE

The Player explicitly checks the current orientation and uses separate layout compositions for portrait and landscape.

Do not remove the orientation-specific layout merely to reduce file size.

Both layouts must preserve:

- current track information;
- seek interaction;
- technical metadata;
- playback controls;
- lyrics toggle;
- close/collapse action.

The fullscreen lyrics layout is selected before the portrait/landscape layout when `isLyricsVisible`
is true.

Portrait/landscape rotation must not collapse the UnifiedPlayerSheet or dismiss fullscreen lyrics merely
because the Activity is recreated.

---

# 30. PLAYER TOP ACTION GROUP

Player uses a Material 3 Expressive `ButtonGroup` for the top action controls.

Current actions are:

```text
Close Player
Lyrics
```

The close button collapses the Player sheet.

The lyrics button toggles artwork/lyrics content.

The lyrics button remains part of the current Player feature because embedded lyric viewing is implemented.

---

# 31. PLAYBACK BUTTON GROUP

The Player playback controls use `ButtonGroup` and filled expressive buttons.

Current controls:

```text
Previous
Play/Pause
Next
```

The previous/next controls use surface-container colors.

Play/Pause uses the primary container.

Button widths participate in expressive pressed-state animation through `animateWidth()`.

---

# 32. PLAYER SEEK BAR

The current Player uses a custom Compose `Canvas` wavy seek bar.

It supports:

- tap to seek;
- drag to seek;
- current position visualization;
- animated wave motion;
- duration display.

While seeking, Player keeps local seek state so playback-state updates do not fight the user's finger movement.

When the gesture ends, the controller receives the final seek position.

Do not replace this with a non-interactive decorative indicator.

---

# 33. TRACK TECHNICAL INFORMATION

`Track` contains real technical fields:

```text
mimeType
sampleRateHz
bitrateBps
```

Player currently displays information derived from these values, including:

```text
sample rate
bitrate
audio format
```

A missing value should remain a missing/unknown value, not become a fabricated capability claim.

Do not label files as:

```text
Hi-Res
Bit Perfect
Native DSD
USB DAC
```

unless the corresponding pipeline is actually implemented and verified.

The track-information presentation also supports extended metadata such as:

```text
release date
label
copyright
release type
```

These values are sourced from real metadata when available. Missing values remain visibly
missing/unknown rather than being omitted simply to make the sheet appear complete.

The track-information rows are kept structurally consistent even when a specific metadata value is
not available.

---

# 34. TRACK MODEL

Current `Track` model:

```kotlin
data class Track(
    val id: Long,
    val uri: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val dateAddedEpochSeconds: Long,
    val mimeType: String? = null,
    val sampleRateHz: Int? = null,
    val bitrateBps: Int? = null,
    val artworkUri: String?,
    val releaseDate: String? = null,
    val label: String? = null,
    val copyright: String? = null,
    val releaseType: String? = null,
)
```

Important semantics:

- `title` is non-null;
- `artist` may be null;
- `album` may be null;
- technical metadata may be null;
- artwork may be null;
- extended release metadata may be null when the source does not expose it.

Do not turn `Track.title` into nullable merely because MediaStore metadata can be incomplete.

The repository keeps a non-null title fallback.

---

# 35. MEDIASTORE CATALOG AND SYNCHRONIZATION

`MediaStoreCatalogDataSource` is the low-level reader for the local audio collection. It queries each
available MediaStore volume and reads version/generation markers, track rows, and lightweight track IDs for
reconciliation. It does not write to Room and does not open every audio file to extract embedded metadata.

`MediaCatalogSyncCoordinator` synchronizes its results into Room's `music_tracks` table. A full scan is used
for first indexing or when MediaStore version/checkpoint conditions require one; incremental scans query rows
whose `GENERATION_MODIFIED` is newer than the saved checkpoint. Unchanged volume version/generation markers
allow the coordinator to skip the full audio-row query. Catalog deletions are reconciled against IDs from
MediaStore. A currently unavailable volume must not be treated as an empty volume.

`RoomMusicCatalogRepository` is the active app-level `MusicRepository`. Home, Library, Search, and active Player
metadata use its database-backed Flows. `MediaStoreMusicRepository` may remain in the source tree, but it is
not the repository wired by `AppContainer` for the primary catalog read path. Do not reintroduce the old
per-track `MediaMetadataRetriever` listing path as the Home/Library/Search startup path.

The MediaStore-derived `Track` uses the real MediaStore content URI for both `uri` and `artworkUri`. Artwork
loading remains a separate operation handled by the existing Android thumbnail component.

---

# 36. MEDIASTORE TITLE / ARTIST / ALBUM SEMANTICS

Title:

- use `TITLE` when non-blank;
- fall back to `DISPLAY_NAME` if necessary;
- remain non-null in `Track`.

Artist and album:

- blank values are treated as missing;
- `<unknown>` is treated as missing;
- presentation-layer localization handles missing artist text.

The data layer must not hardcode localized user-facing strings such as `Unknown artist` or `Artis tidak diketahui`.

---

# 37. RECENTLY ADDED

Home observes the Room-backed catalog through:

```text
MusicRepository.observeRecentlyAdded(limit = 10)
```

The list is sorted using real MediaStore `DATE_ADDED` values. Room emits updates when catalog rows change;
Home must not launch a full MediaStore scan or open every audio file just to render this section.
Do not fabricate recently added tracks.

---

# 38. LIBRARY

Library has two explicit content modes:

```text
All Songs
Folders
```

The selector uses Material 3 Expressive `ButtonGroup` APIs rather than a custom segmented control.

All Songs mode observes the complete synchronized local audio catalog through `MusicRepository.observeAllTracks()`.
Room provides the reactive read model; MediaStore synchronization is handled separately in `:data:media-store`.

The Library sort control is context-aware. `LibrarySortBottomSheet` shows only the options supported by the active `LibraryBrowseMode`; sorting is applied to the real Room-backed catalog/collection data and must not trigger a MediaStore rescan. The exact sort option matrix is documented in Section 38.3.

Folders mode uses `FolderBrowserRepository` and renders the MediaStore-derived folder hierarchy.

The Library does not rescan the filesystem when switching between these modes.

The Library renders real tracks through `TrackRow`. Context-specific sort options and Artist/Album/Genre artwork rules are specified in Section 38.3; that section is the authoritative Library sorting contract.

The current Library header is a native Material 3 `TopAppBar`, kept outside the scrolling list so
the app bar remains stable while folder contents change.

When the user enters a folder, the Back icon is introduced through the native `TopAppBar.navigationIcon`
slot using `AnimatedVisibility`. The slot expands horizontally from the start, so the `Library` title
moves continuously as the icon slot gains width instead of jumping to a new position.

When leaving the folder, the same slot shrinks horizontally and the title returns continuously.

The current interaction is:

```text
All Songs / root Folders
        ↓
enter a folder
        ↓
[Back] Library
```

The current implementation uses:

```kotlin
AnimatedVisibility(
    visible = currentFolderPath != null,
    enter =
        expandHorizontally(
            expandFrom = Alignment.Start,
            animationSpec =
                MaterialTheme.motionScheme
                    .defaultSpatialSpec(),
        ) +
            fadeIn(
                animationSpec =
                    MaterialTheme.motionScheme
                        .defaultEffectsSpec(),
            ),
    exit =
        shrinkHorizontally(
            shrinkTowards = Alignment.Start,
            animationSpec =
                MaterialTheme.motionScheme
                    .defaultSpatialSpec(),
        ) +
            fadeOut(
                animationSpec =
                    MaterialTheme.motionScheme
                        .defaultEffectsSpec(),
            ),
)
```

This is a Library header interaction, not a Navigation 3 page transition.

Do not replace the behavior with a manual title `offset()` or a custom top-app-bar `Row`. The native
`TopAppBar.navigationIcon` slot must remain responsible for layout so the title naturally follows the
animated slot width.

Preserve this interaction when modifying folder navigation.

---

# 38.1. FOLDER BROWSING AND TAPABLE NAVIGATION PATH

Folder browsing is a first-class Library mode.

The current flow is:

```text
Library
   ↓
Folders mode
   ↓
root folders
   ↓
tap a folder
   ↓
nested folder
   ↓
tap a breadcrumb/path segment
   ↓
selected ancestor folder
```

The current `LibraryViewModel` owns:

```text
openFolder(relativePath)
goToParentFolder()
showAllSongs()
showRootFolders()
```

The current open/navigation folder path is kept as application UI state rather than stored in the persistent database. The user's Folder Filter selections are persistent database state.

The current UI state tracks:

```text
folders
folderTracks
currentFolderPath
```

Folder paths are normalized consistently before being stored in UI state so root/child navigation does
not produce duplicate path representations.

The breadcrumb/navigation path is interactive. A path segment is a real tap target and navigates to
that folder level instead of being decorative text.

Do not replace the tapable path with a plain `Text` row.

Do not force a MediaStore rescan when the user opens a folder or taps a breadcrumb. Folder navigation
operates on the repository's MediaStore-derived dataset.

---

# 38.2. FOLDER MODE EMPTY STATES

Folder mode must distinguish:

```text
root has no folders
current folder has no tracks
current folder has child folders
current folder has no child folders
```

A missing folder entry must never be replaced with demo folders or fabricated paths.

The same no-fake-data rule applies to folder artwork, names, and track counts.

---

# 38.3. LIBRARY SORTING AND COLLECTION ARTWORK

The available sort options are defined by `LibrarySort.kt` and presented by `LibrarySortBottomSheet.kt`. Do not reintroduce Duration as a sort option.

| Browse context | Supported options |
|---|---|
| All Songs and Folder | Default, Title, Artist, Album, Genre, Date added, Date modified |
| Artist collection list | Default, Artist, Date added, Date modified |
| Album collection list | Default, Album, Date added, Date modified |
| Genre collection list | Default, Genre, Date added, Date modified |
| Songs inside a selected Artist / Album / Genre collection | Default, Title, Artist, Album, Genre, Date added, Date modified |

Rules:

- The sort sheet must filter its visible options to match the current browse context; do not show unsupported options just because they exist in the global enum.
- `Genre` sorting must use the actual genre metadata on the track/collection, not title, album, or a fabricated genre label.
- Date added and Date modified must use their respective available source metadata.
- `Default` preserves the default ordering implemented by the current Library logic. Do not redefine it based solely on the appearance of one screen.
- Artist, Album, and Genre collection entries use real associated artwork when it is available. Artist artwork is circular; Album artwork uses its established rounded-rectangle presentation; Genre artwork uses the established `Cookie9Sided` expressive shape. Keep the established 56dp artwork slot where this list contract applies.
- Missing artwork or metadata must remain a real unavailable state; do not generate placeholder images or labels.
- Sorting must remain UI/data transformation over repository results. Opening a sort sheet or changing sort order must not rescan MediaStore or open every audio file.
- Preserve the context-specific options when adding a new sort value. Update the enum, comparator, and filtering/UI labels together.

---

# 39. RECENT PLAY

Recent Play is persistent across application restarts and retains the latest-played record for every distinct content URI known to the history. There is no hard 10-record cap in the database. The primary key is `uri`, not `track_id`, because MediaStore IDs can overlap across storage volumes.

This is a per-track recency list, not an event ledger: playing the same URI again updates/replaces its record and its `last_played_at_epoch_millis` rather than adding another row for every playback occurrence. The Home preview is separately limited to 10 entries; do not apply that UI limit to persistence.

The playback flow is:

```text
Media3PlaybackController
        ↓
RecentPlayRepository
        ↓
RoomRecentPlayRepository
        ↓
RecentPlayDao
        ↓
recent_plays
        ↓
SQLite
```

Recording behavior:

1. Upsert the record for the content URI, replacing/updating any existing record for that URI.
2. Update `last_played_at_epoch_millis` so the URI returns to the newest position in the recency ordering.
3. Retain all distinct-URI records; do not trim the database to the Home preview size.
4. Expose the complete database-backed history through the existing Recent Play repository/Flow. Home applies its own 10-entry presentation limit.
5. When embedded `releaseDate`/`copyright` metadata is later extracted for the same URI, update those fields in the Recent Play snapshot without creating a new playback event.

Recent Play is an application-owned playback-history snapshot. It does not modify the underlying media file;
MediaStore remains authoritative for media availability and indexed source metadata.

---

# 40. RECENT PLAY EVENT SOURCE

Recent Play is recorded by the Media3 playback layer when a track actually becomes active. The playback layer writes through `RecentPlayRepository`; it does not access Room directly.

The implementation listens to relevant Media3 player events such as:

```text
onIsPlayingChanged
onMediaItemTransition
onPlaybackStateChanged
```

A simple UI tap should not be treated as a playback-history event.

---

# 41. PLAYBACK CONTROLLER CONTRACT

`PlaybackController` currently exposes:

```kotlin
val playbackState: StateFlow<PlaybackState>
val recentPlays: StateFlow<List<Track>>

fun play(track: Track)

fun playQueue(
    tracks: List<Track>,
    startIndex: Int = 0,
)

fun pause()
fun resume()
fun stopAndClearQueue()
fun seekTo(positionMs: Long)
fun skipToNext()
fun skipToPrevious()
```

`recentPlays` exposes the complete persistent Recent Play recency list (one record per distinct content URI), not the 10-entry Home preview. Presentation layers apply any display limit themselves.

Feature modules should depend on this interface.

---

# 42. PLAYBACK STATE

`PlaybackState` is the application-level playback state.

It contains the current track and playback position/duration state needed by UI.

The UI observes it through `StateFlow`.

Do not duplicate Media3 state into a second global player state system.

---

# 43. QUEUE BEHAVIOR

The playback queue is in memory.

When a screen selects a track from a list:

```text
tracks + selected index
        ↓
PlaybackController.playQueue(...)
```

The selected item becomes current and Previous/Next operate within that queue.

The queue does not survive process death.

Do not introduce persistent playlists.

---

# 44. MEDIA3 IMPLEMENTATION

`Media3PlaybackController` creates a `SessionToken` for:

```text
RoviaMediaSessionService
```

It obtains a `MediaController` through the official Media3 API.

The controller keeps a local queue of `Track` objects and translates it into Media3 `MediaItem`s.

Features provided through the controller include:

```text
play
playQueue
pause
resume
stop/clear
seek
next
previous
position updates
```

---

# 45. MEDIASESSION SERVICE

`RoviaMediaSessionService` owns:

```text
ExoPlayer
MediaSession
```

It is registered in the manifest as a `MediaSessionService`.

The service is the background playback boundary.

Do not construct another ExoPlayer in `MainActivity`, `PlayerScreen`, or a feature ViewModel.

---

# 46. BACKGROUND PLAYBACK

The playback service uses Media3's system media-session integration for:

- background playback;
- Android media notification;
- lock-screen controls;
- system media controls.

Rovia does not maintain a second custom notification state machine.

---

# 47. MEDIA METADATA

Media3 `MediaItem` metadata is created from real track information.

Do not invent album/artist/title metadata when the source is missing.

Do not manipulate source metadata merely for UI translation.

Localization changes UI labels, not the music metadata itself.

---

# 48. ARTWORK

`AlbumArtwork` uses Android's local thumbnail APIs through `ContentResolver.loadThumbnail()`.

The component loads thumbnails on a background dispatcher.

There is no Coil or Glide dependency.

If artwork is unavailable, a neutral Material-themed fallback is rendered.

The fallback is derived from the real title, not generated network content.

---

# 49. ARTWORK CONTRACT

Artwork must be:

- local;
- real;
- lightweight;
- optional.

Do not add remote image URLs or a network image loader.

Do not decode unnecessarily large bitmaps.

---

# 50. SEARCH SCREEN

Search is local and realtime.

The Search UI uses Material 3 Expressive search input behavior.

The user can search local tracks by title, artist, and album.

The Search screen does not require an account or network connection.

---

# 51. SEARCH MATCHING

Current search behavior includes:

- normalization;
- diacritic removal;
- lowercase normalization with `Locale.ROOT`;
- whitespace normalization;
- token matching;
- title matching;
- artist weighting;
- album weighting;
- fuzzy matching through Levenshtein distance;
- result limiting.

Current constants include:

```text
MAX_RESULTS       = 50
ARTIST_WEIGHT     = 0.85
ALBUM_WEIGHT      = 0.65
FUZZY_THRESHOLD   = 0.55
```

Do not replace this with a simple `String.contains()` search without an explicit behavior decision.

---

# 52. SEARCH STATES

Search has real states for:

```text
Loading
Blank query
Content
No results
Error
```

Blank query and no results are intentionally different states.

---

# 53. HOME SCREEN

Home contains:

```text
Settings button
Recent Play preview (latest 10 distinct-URI records)
Recent Play collections: Artist / Album / Genre
Recently Added
```

The Recent Play preview is a presentation limit only. The persisted history and the Artist/Album/Genre collections are derived from the complete database-backed distinct-URI history. Opening a collection leads to a dedicated detail page whose track list is derived from real Recent Play records for that collection.

The Home Recent Play preview uses a horizontal two-row artwork grid when enough entries exist. Collection group entries use the actual collection label and associated artwork when available. Do not invent artists, albums, genres, artwork, or track counts.

Recently Added uses `TrackRow`.

If a section has no data, it remains empty instead of showing fake tracks.

---

# 54. TRACK ROW

`TrackRow` currently renders:

```text
artwork
track title
artist / album supporting text
formatted duration
```

Supporting text is assembled only from non-null actual metadata.

---

# 55. PLAYER SCREEN AND LYRICS

Rovia **does currently implement lyrics**.

This section supersedes older milestone documentation that may have said Lyrics were deferred.

Lyrics are local and embedded in the audio file.

There is no online lyric provider.

The Player includes a Lyrics action.

---

# 56. LYRICS ARCHITECTURE

The current lyric flow is:

```text
PlayerScreen
    ↑
PlayerViewModel
    ↑
LyricsRepository
    ↑
EmbeddedLyricsRepository
    ↑
local audio file
```

`LyricsRepository` is an API interface.

`EmbeddedLyricsRepository` is the current implementation.

Do not add network lyrics.

---

# 57. LYRIC DATA MODEL

Current model:

```kotlin
data class SyncedLyrics(
    val lines: List<LyricLine>,
)

data class LyricLine(
    val startTimeMs: Long,
    val words: List<LyricWord>,
)

data class LyricWord(
    val startTimeMs: Long,
    val text: String,
)
```

This supports both line-level and word-level synchronization.

---

# 58. LYRIC PARSING

The embedded parser recognizes timestamped lyrics and handles relevant embedded lyric metadata.

Current timestamp model includes:

```text
[line timestamp]
<word timestamp>
```

The parser ignores relevant LRC metadata/header lines when appropriate and normalizes milliseconds.

Do not replace it with a generic online lyric service.

---

# 59. LYRIC SYNCHRONIZATION

`LyricViewer` compares:

```text
playback position in milliseconds
```

with:

```text
LyricLine.startTimeMs
LyricWord.startTimeMs
```

It determines:

```text
active line
active word
```

The active line scrolls into view.

The active line receives a stronger visual emphasis through a smooth scale animation. The scale is
adaptive for long lines so text remains usable instead of growing excessively.

The active word gets stronger visual emphasis and primary coloring.

---

# 60. ARTWORK <-> LYRICS TOGGLE

The Player uses `AnimatedContent` to switch between:

```text
Album artwork
```

and:

```text
LyricViewer
```

Lyrics are not rendered on top of the album artwork.

The toggle state is owned by `PlayerViewModel`.

When lyrics are visible in the Player, the PlayerScreen replaces the normal Player content with
`PlayerLyricsFullscreenContent`. The album artwork is therefore not layered underneath the fullscreen
lyrics view.

The fullscreen lyrics mode remains active while Previous/Next changes the current track. The lyric
content itself is cleared and reloaded for the new track, but `isLyricsVisible` is not automatically
reset during that track transition.

Closing the Player explicitly hides lyrics before the Player sheet is closed.

---

# 61. LYRIC STATES

Current Player lyric state distinguishes:

```text
loading
available
visible
hidden
unavailable
```

When a new track becomes current:

```text
lyrics content -> clear
lyrics loading  -> reload for the new track
lyrics visibility -> preserved if fullscreen lyrics was already visible
```

This allows fullscreen lyrics to remain open while Previous/Next moves between tracks.

The explicit Player close operation still hides lyrics before the Player sheet is closed.

The ViewModel then loads the new track's embedded lyrics.

---

# 62. PLAYER VIEWMODEL

`PlayerViewModel` combines:

```text
PlaybackController.playbackState
lyrics StateFlow
isLyricsLoading
isLyricsVisible
```

into `PlayerUiState`.

When the current track ID changes, the ViewModel cancels the previous lyric load, clears the
currently loaded lyric content, and starts loading lyrics for the new track.

A current fullscreen lyrics session does not automatically hide itself merely because the current track
changed. `isLyricsVisible` is controlled explicitly by the Player lyrics toggle/close behavior.

`hideLyrics()` explicitly clears the fullscreen-visible state when the Player is closed.

Exceptions from lyric loading are treated as lyric unavailability rather than causing fake data to appear.

---

# 63. SETTINGS AND FOLDER FILTER

The Settings destination uses a native Material 3 `TopAppBar`.

Its header contract is:

```text
TopAppBar
horizontal outer padding = 4dp
navigation icon = back action
action slot = screen-specific action where implemented
```

The current Settings surface contains:

```text
Settings TopAppBar
Settings content
```

The Folder Filter screen is a concrete screen in the current source tree. It also uses the same
native `TopAppBar` pattern so the header position and spacing remain consistent with Settings.

Folder Filter selections are persistent and are stored by `RoomFolderFilterRepository`. The scanned
folder list itself still comes from MediaStore through `FolderScannerRepository`.

The Folder Filter header currently exposes:

```text
back action
add action
```

The Folder Filter screen uses the native Material 3 / Expressive component defaults for:

- top app bar actions;
- folder list items;
- button shapes;
- icon button shapes.

There is no language picker in Settings.

Settings itself does not persist a general set of preference values. Application-level persistent data includes Folder Filter selections and the Recent Play history; those are not Settings preference records.

The application language follows Android system resources automatically.

---

# 64. MULTI-LANGUAGE SUPPORT

Rovia currently supports:

```text
English
Indonesian
```

The implementation uses Android resource localization.

No custom language manager is required.

No database or preference is used for language selection.

---

# 65. LOCALIZATION RESOURCE RULE

The current resource layout uses:

```text
res/values/strings.xml
res/values-in/strings.xml
```

for the relevant modules.

`values` is the English/default resource set.

`values-in` is the current Indonesian resource set used by this project.

Do not casually rename localization folders or invent a second localization system.

---

# 66. LOCALIZATION OWNERSHIP

Strings are kept in the module that owns the corresponding UI.

Examples:

```text
app
    permission strings
    bottom navigation strings

core/ui
    shared playback action strings

feature/home
    Home section and empty/error strings

feature/search
    Search strings

feature/library
    Library strings

feature/player
    Player and lyrics strings

feature/settings
    Settings strings
```

This prevents the app module from becoming a global string dump.

---

# 67. LOCALIZATION DATA BOUNDARY

UI labels must be localized.

Music metadata must not be translated.

For example:

```text
UI:
Previous -> Sebelumnya
Pause    -> Jeda
Search   -> Cari
```

But real metadata remains unchanged:

```text
Muse
Absolution
Hysteria
```

The MediaStore repository must not contain language-specific labels such as `Unknown artist`.

The UI uses the appropriate localized resource for missing presentation data.

---

# 68. ANDROID RESOURCE `R` NAMESPACES

Every Android module has its own `R` namespace.

Examples:

```text
com.rovia.music.R
com.rovia.music.core.ui.R
com.rovia.music.feature.player.R
com.rovia.music.feature.home.R
```

When a feature needs shared drawable resources from `core:ui`, an alias is used where necessary, for example:

```kotlin
import com.rovia.music.core.ui.R as CoreUiR
```

The feature's own strings remain in its feature module resource namespace.

Do not move resources across modules simply to avoid an import alias.

---

# 69. ACCESSIBILITY

Interactive icons use meaningful localized content descriptions when their purpose is not already communicated by visible text.

Current examples include:

```text
Home
Search
Library
Settings
Close player
Previous
Play
Pause
Next
Show lyrics
Hide lyrics
```

Accessibility strings follow the same language resources as the visible UI.

---

# 70. ICON POLICY

Do not add an entire icon library just for a few icons.

Current UI icons are mostly local vector drawables owned by the appropriate module.

For in-app icons, the vector resource is only the icon shape. The final visual color must come
from the current Material theme.

Preferred Compose pattern:

```kotlin
Icon(
    painter = painterResource(R.drawable.ic_example),
    tint = MaterialTheme.colorScheme.onSurfaceVariant,
)
```

When an icon is inside an official Material component, prefer the component's content-color
resolution instead of manually forcing a fixed tint.

Verified NavigationBar pattern:

```text
NavigationBar
    ↓
NavigationBarItemDefaults.colors(...)
    ↓
MaterialTheme.colorScheme semantic roles
```

Do not add a hardcoded icon color simply because a custom ROM currently renders one surface as
pure black.

If an icon appears incorrect on a device, first inspect:

```text
theme -> component default -> semantic color role -> icon tint
```

before changing the vector drawable itself.

Examples:

```text
ic_play_arrow
ic_pause
ic_skip_previous
ic_skip_next
ic_keyboard_arrow_down
ic_settings
ic_search
ic_lyrics
ic_arrow_back
```

Do not replace these with emoji characters.

---

# 71. THEMED APP ICON

The application includes the Android adaptive icon structure and a monochrome resource.

Current resources include:

```text
res/mipmap-anydpi-v26/ic_launcher.xml
res/mipmap-anydpi-v26/ic_launcher_round.xml
res/drawable/ic_launcher_monochrome.xml
```

Do not replace the monochrome layer with a UI icon or add a full icon library.

---

# 72. EDGE-TO-EDGE

The Activity enables edge-to-edge.

Top-level navigation applies status-bar handling.

The Player also handles its own top inset in its content.

Bottom navigation and MiniPlayer are composed at the root level so their positions remain consistent across destinations.

Do not add arbitrary screen-level inset padding everywhere.

---

# 73. STATE MANAGEMENT

The general state direction is:

```text
repository / controller
        ↓
ViewModel / UI state
        ↓
Compose
        ↓
user event
        ↓
ViewModel / controller
```

Use lifecycle-aware Compose state collection where the project already does so.

Do not create a second global mutable playback state.

Do not let screens construct services directly.

---

# 74. HOME VIEWMODEL

`HomeViewModel` observes:

```text
MusicRepository.observeRecentlyAdded(limit = 10)
PlaybackController.recentPlays
```

and exposes a `HomeUiState` through `StateFlow`. Recently Added comes from Room's synchronized catalog. `PlaybackController.recentPlays` exposes the complete persistent distinct-URI Recent Play history; `HomeViewModel` limits only the Home preview to 10 and derives Artist/Album/Genre collection groups from the history records. Collection detail state is based on the same persisted history. Errors are represented as actual error states.

---

# 75. LIBRARY VIEWMODEL

`LibraryViewModel` observes:

```text
MusicRepository.observeAllTracks()
```

and exposes a `LibraryUiState` through `StateFlow`. Track listings come from the Room-backed synchronized
catalog, while folder discovery/browsing continues to use the dedicated MediaStore folder repositories.
Excluded folders are applied by the catalog query and the existing Folder Filter repository.

---

# 76. SEARCH VIEWMODEL

`SearchRoute` observes text changes from Compose `TextFieldState` and forwards the query to `SearchViewModel`.

`SearchViewModel` observes `MusicRepository.observeAllTracks()` and performs local fuzzy matching over the
Room-backed synchronized catalog. Search remains offline and exposes a bounded result list (currently 50).
It does not independently query MediaStore or open audio files per query. No network search is involved.

---

# 77. PLAYER ROUTE

`PlayerRoute` creates `PlayerViewModel` using `PlayerViewModelFactory`.

It collects `PlayerUiState` and passes plain state/callbacks to `PlayerScreen`.

This keeps the Player composable free of direct Media3 implementation details.

---

# 78. ERROR HANDLING RULE

Errors must represent actual failures.

Examples:

```text
Permission denied
MediaStore query failed
Playback failed
Artwork unavailable
Lyrics unavailable
```

Do not convert exceptions into fake successful data.

Do not hide actual playback failures with placeholder tracks.

---

# 79. PERFORMANCE RULES

Keep Rovia intentionally lightweight.

Current important constraints:

- Home Recently Added is limited to 10.
- Recent Play persistence retains every distinct content URI; only the Home preview is limited to 10 entries.
- Search exposes at most 50 results.
- Home, Library, Search, and active-player metadata read from Room-backed catalog Flows.
- MediaStore remains the source of truth for media availability and indexed source values; `music_tracks` is
  intentionally a synchronized local catalog, not a user-data-only table.
- Use per-volume MediaStore version/generation checkpoints to avoid querying the entire catalog when nothing
  changed and use incremental synchronization when supported by the current checkpoint.
- Do not run `MediaMetadataRetriever` as part of catalog listing for every track. Enrich selected embedded tags
  separately in bounded batches.
- Recent Play is ordered by last-played time and is not trimmed to the Home preview size.
- Album artwork uses Android thumbnail loading.
- There is only one playback service/player path.
- The app does not continuously scan the raw filesystem.
- No network image loading exists.
- No background worker is introduced without a real requirement.

Do not add new cache layers or another media database before they are needed. Preserve the separation between
MediaStore synchronization, Room catalog reads, embedded metadata enrichment, and Media3 playback.

---

# 80. RELEASE BUILD POLICY

Release configuration currently enables:

```text
R8/minification
resource shrinking
```

Do not claim an exact APK size until a real release build has been measured.

Do not add dependencies simply because a feature could be implemented with a larger library.

---

# 81. FEATURES THAT ARE NOT CURRENTLY IMPLEMENTED

The following must remain absent unless explicitly requested and implemented as a separate milestone:

```text
Persistent favorites
Persistent playlists
Most Played
Streaming
Online lyrics
Accounts / login / OAuth
Cloud sync
Remote metadata services
Download manager
Equalizer / custom DSP
ReplayGain
Crossfade engine
Gapless custom engine
DSD-specific custom decoder
DoP
USB DAC custom driver
Bit-perfect verification engine
Custom resampler
FFmpeg / FFprobe
Transcoding
YouTube Music / Spotify integrations
AI/MCP features
Analytics SDK
Advertising
Chromecast
Social features
Subscriptions
```

The existence of a Settings page does not mean these features belong in Settings.

---

# 82. AUDIO FORMAT POLICY

Rovia uses the normal Media3/ExoPlayer/Android decoder path.

Do not silently transcode unsupported audio.

Do not add FFmpeg just to broaden codec coverage.

Actual device capability should determine playback behavior.

The application must not label an audio file as Hi-Res/Bit-Perfect/DSD merely because metadata looks high quality.

---

# 83. DATABASE POLICY

Rovia uses Room 3 over Android SQLite in `:data:database`.

Current schema version:

```text
1
```

Current tables:

```text
music_tracks
media_store_sync_state
recent_plays
excluded_folders
```

Responsibilities:

- `music_tracks`: synchronized local audio catalog and metadata/enrichment state;
- `media_store_sync_state`: per-volume MediaStore version/generation checkpoints;
- `recent_plays`: persistent history keyed by distinct content URI, with no hard 10-record cap;
- `excluded_folders`: persisted Folder Filter selections.

The catalog is intentionally persisted in Room for fast/reactive reads. MediaStore remains the authority for
underlying local files, mounted-volume availability, content URIs, relative paths, and indexed source values;
Room's catalog must be synchronized against it. Do not describe `music_tracks` as a table that must not exist
or must not mirror a synchronized local track inventory.

Room/SQLite implementation details remain inside `:data:database`; app and feature code use Core repository
interfaces. Do not introduce a second persistence technology for these responsibilities.

The current development policy keeps the schema at version `1` and does not add migration/automigration work
unless explicitly requested. When a local schema change makes an existing development database incompatible,
clearing app data or reinstalling is acceptable during development. Before shipping schema changes to users
whose existing data must be preserved, establish an explicit released schema baseline and migration plan.
Do not use destructive fallback migration as a production recovery strategy.

The database does not currently persist:

```text
Favorites
Most Played
Playlists
queue state
open folder/navigation path
language selection
```

Language follows Android resources. Settings remains a UI shell except for explicitly implemented persistent
data such as Folder Filter selections.

---

# 84. NETWORK POLICY

Rovia currently has no network requirement.

Do not add:

```text
INTERNET permission
HTTP clients
remote album art
online lyrics
cloud sync
streaming services
```

unless the product scope explicitly changes.

---

# 85. NO FAKE CAPABILITY LABELS

Do not display:

```text
Hi-Res
Bit Perfect
Native DSD
USB DAC
Lossless verified
```

unless the corresponding state is truly measured or implemented.

A real sample rate field is not the same as a verified end-to-end bit-perfect claim.

---

# 86. SAFE MODIFICATION PROCEDURE FOR AI AGENTS

When asked to change Rovia, follow this workflow exactly.

## Step 1 — Read the engineering contract

Read `Agents.md` before modifying the project. Treat the implementation as an existing application, not a blank template.

## Step 2 — Inspect the current file

Before modifying an existing file, inspect its latest full contents. If those contents are not already available, ask the developer to send the complete current file first. Do not guess from snippets, reconstruct unseen code, or make partial edits to a file whose current structure is unknown.

## Step 3 — Create/open files through Windows PowerShell and VS Code

For a new file, provide a PowerShell command to create its parent directory/file and a `code <relative-path>` command to open it in VS Code. Always provide the complete source file, ready to paste. For an existing file, provide the complete updated file rather than isolated fragments unless the developer explicitly asks for a minimal diff.

## Step 4 — One source-file change per build checkpoint

Keep the requested change small and file-scoped. For application source/code changes, use one file per build checkpoint. Markdown-only documentation changes do not require a Gradle checkpoint. A checkpoint is a workflow step, not a Git commit or tag. Do not create an interim commit merely because a build checkpoint has been reached.

## Step 5 — Preserve unrelated behavior

Do not touch unrelated architecture, files, modules, UI behavior, or dependencies. Prefer the smallest change that fulfils the requested behavior.

## Step 6 — Build after each source-file change

After the developer pastes/saves an application source/code change, ask them to run:

```powershell
.\gradlew.bat :app:assembleDebug
```

Stop and wait for the actual build output before proceeding to another source file. Do not make further source changes while that checkpoint is unresolved. A documentation-only edit such as `Agents.md` does not need this Gradle build.

## Step 7 — Resolve errors before continuing

If the build fails, fix the actual reported error in the current step. Do not proceed to another planned change, install the APK, or claim success while the build fails.

## Step 8 — Install after all intended code changes compile

After all intended application source/code changes pass their build checkpoints, run:

```powershell
.\gradlew.bat :app:installDebug
```

Do not install an APK for a documentation-only change.

## Step 9 — Verify runtime behavior

Run the relevant runtime/regression checks on the Android device or emulator. Do not claim runtime behavior is verified until the user reports the observed result or an actual test provides it.

## Step 10 — Commit only when explicitly requested

Do not create Git commits automatically. When the developer explicitly asks for commits, use the PowerShell Git workflow in Section 90.1. The project preference is one changed file per commit, with an accurate ownership prefix and an English message.

## Step 11 — Keep staging precise

When committing is requested, stage only the exact file being committed. Do not use `git add .` or `git commit -a` for the per-file workflow.

## Step 12 — Synchronize only when explicitly requested

Inspect the commit history and `git status -sb` when needed. Do not push or sync automatically; run `git push origin main` only when the developer explicitly requests it.

A failed build or failed runtime verification is a checkpoint for fixing the current step, not a reason to make unrelated changes or create a misleading successful commit.

---

# 87. RISKY AREAS

Treat these files/modules as high-impact:

```text
app/src/main/kotlin/com/rovia/music/Navigation.kt
app/src/main/kotlin/com/rovia/music/UnifiedPlayerSheet.kt
app/src/main/kotlin/com/rovia/music/MainActivity.kt
app/src/main/kotlin/com/rovia/music/theme/Theme.kt
core/playback-api/src/main/kotlin/com/rovia/music/core/playback/PlaybackController.kt
playback/media3/src/main/kotlin/com/rovia/music/playback/media3/Media3PlaybackController.kt
playback/media3/src/main/kotlin/com/rovia/music/playback/media3/RoviaMediaSessionService.kt
data/database/src/main/kotlin/com/rovia/music/data/database/RoviaDatabase.kt
data/database/src/main/kotlin/com/rovia/music/data/database/RoviaDatabaseProvider.kt
data/database/src/main/kotlin/com/rovia/music/data/database/dao/RecentPlayDao.kt
data/database/src/main/kotlin/com/rovia/music/data/database/dao/ExcludedFolderDao.kt
data/database/src/main/kotlin/com/rovia/music/data/database/dao/MusicTrackDao.kt
data/database/src/main/kotlin/com/rovia/music/data/database/dao/MediaStoreSyncStateDao.kt
data/database/src/main/kotlin/com/rovia/music/data/database/repository/RoomMusicCatalogRepository.kt
data/database/src/main/kotlin/com/rovia/music/data/database/repository/RoomRecentPlayRepository.kt
data/database/src/main/kotlin/com/rovia/music/data/database/repository/RoomFolderFilterRepository.kt
feature/home/src/main/kotlin/com/rovia/music/feature/home/HomeViewModel.kt
feature/home/src/main/kotlin/com/rovia/music/feature/home/RecentCollectionRoute.kt
feature/home/src/main/kotlin/com/rovia/music/feature/home/RecentCollectionScreen.kt
feature/library/src/main/kotlin/com/rovia/music/feature/library/LibraryScreen.kt
feature/library/src/main/kotlin/com/rovia/music/feature/library/LibrarySort.kt
feature/library/src/main/kotlin/com/rovia/music/feature/library/LibrarySortBottomSheet.kt
data/media-store/src/main/kotlin/com/rovia/music/data/media/store/MediaStoreMusicRepository.kt (legacy; not the AppContainer catalog read path)
data/media-store/src/main/kotlin/com/rovia/music/data/media/store/MediaStoreCatalogDataSource.kt
data/media-store/src/main/kotlin/com/rovia/music/data/media/store/MediaCatalogSyncCoordinator.kt
data/media-store/src/main/kotlin/com/rovia/music/data/media/store/EmbeddedMetadataEnrichmentProcessor.kt
data/media-store/src/main/kotlin/com/rovia/music/data/media/store/EmbeddedMetadataSignature.kt
data/media-store/src/main/kotlin/com/rovia/music/data/media/store/EmbeddedLyricsRepository.kt
feature/player/src/main/kotlin/com/rovia/music/feature/player/PlayerScreen.kt
feature/player/src/main/kotlin/com/rovia/music/feature/player/PlayerPortraitContent.kt
feature/player/src/main/kotlin/com/rovia/music/feature/player/PlayerLandscapeContent.kt
feature/player/src/main/kotlin/com/rovia/music/feature/player/PlayerArtwork.kt
feature/player/src/main/kotlin/com/rovia/music/feature/player/PlayerLyric.kt
feature/player/src/main/kotlin/com/rovia/music/feature/player/PlayerActionButtonGroup.kt
feature/player/src/main/kotlin/com/rovia/music/feature/player/PlayerPlaybackControls.kt
feature/player/src/main/kotlin/com/rovia/music/feature/player/PlayerSeekBar.kt
feature/player/src/main/kotlin/com/rovia/music/feature/player/PlayerTrackInfo.kt
feature/player/src/main/kotlin/com/rovia/music/feature/player/PlayerViewModel.kt
feature/player/src/main/kotlin/com/rovia/music/feature/player/LyricViewer.kt
```

Do not make a broad regex-based replacement in these files without inspecting the exact source first.

---

# 88. POWER-SHELL CHANGE WORKFLOW

The project is developed on Windows using PowerShell and VS Code.

For a new file, create the parent directory and empty file with explicit PowerShell commands, then open it with VS Code. For example:

```powershell
$path = '.\path\to\NewFile.kt'
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $path) | Out-Null
New-Item -ItemType File -Force -Path $path | Out-Null
code $path
```

The AI agent must then provide the complete source contents for the developer to paste into the opened file.

For an existing source file, inspect the latest complete contents before modifying it. Return the full updated source file so the developer can replace it without accidentally leaving stale code. Use `code <relative-path>` to open the target file. For a very large documentation file such as this one, provide the complete updated `Agents.md` artifact rather than a truncated partial document; do not package the project as a ZIP unless the developer explicitly asks for a ZIP.

The preferred source-change workflow is incremental and observable:

1. Confirm the repository-relative target path before editing.
2. Inspect the current full file before modifying an existing file.
3. Change one file at a time unless the developer explicitly requests a coordinated batch.
4. After each file-level change, use this build checkpoint and wait for the developer's output:

   ```powershell
   .\gradlew.bat :app:assembleDebug
   ```

5. If the build fails, address that exact error before proceeding.
6. After all intended changes compile, install and perform runtime regression testing:

   ```powershell
   .\gradlew.bat :app:installDebug
   ```

7. Do not use broad regex replacement when a structured file can be edited deterministically.
8. For Kotlin-only source migration, move `.kt` files only; do not modify package declarations. Verify the new tree and compile before removing an empty old source directory.
9. Never run a script that closes the developer's interactive PowerShell session. Do not use `exit` in a script meant to be pasted into PowerShell.
10. Keep the terminal available so actual compiler output can be inspected.

A build checkpoint is a workflow pause, not a Git commit, stash, branch, or tag. Do not create checkpoint commits unless explicitly requested.

---

# 89. BUILD VERIFICATION

The mandatory application-wide Debug verification command after every application source/code change is:

```powershell
.\gradlew.bat :app:assembleDebug
```

This command must be run after the requested application source/code change has been implemented, before that code change is considered complete. Markdown-only documentation changes do not require a Gradle build unless they also change source, resources, or build configuration.

A successful build must end with:

```text
BUILD SUCCESSFUL
```

The preferred verification is the real `:app:assembleDebug` task because it validates the application
assembly rather than only an individual Kotlin compilation task.

The helper script may still be used as an additional developer convenience, but it does not replace the
mandatory command above for the AI-agent change workflow.

Do not claim application-wide build success based only on one feature module unless the requested scope is
explicitly limited to that module.

If `:app:assembleDebug` fails:

```text
DO NOT INSTALL
DO NOT CLAIM SUCCESS
DO NOT CREATE A SUCCESSFUL FEATURE COMMIT
```

Fix the actual reported issue before proceeding.

---

# 90. DEVICE INSTALL

After and only after:

```powershell
.\gradlew.bat :app:assembleDebug
```

finishes successfully, install the current Debug build with:

```powershell
.\gradlew.bat :app:installDebug
```

This is the standard Rovia development installation command for AI-agent verification.

Runtime interaction changes should then be tested on the Android 16 device/emulator when possible.

The required sequence is:

```text
source change
    ↓
.\gradlew.bat :app:assembleDebug
    ↓
BUILD SUCCESSFUL
    ↓
.\gradlew.bat :app:installDebug
    ↓
runtime/regression verification
    ↓
finish implementation; commit only if explicitly requested
```

Do not skip the install step when a successful application-wide Debug build was produced for a runtime-affecting change. Documentation-only changes do not need APK installation.

---

# 90.1. EXPLICIT GIT COMMIT POLICY

Git commits are created only when the developer explicitly requests them. When requested, the Rovia workflow creates **one commit per changed file**, even when several files belong to the same larger feature. This keeps each commit narrowly reviewable without silently changing Git history.

For runtime-affecting changes, the normal verification sequence is:

```text
requested change implemented
        ↓
per-file :app:assembleDebug checkpoints -> SUCCESS
        ↓
all intended changes compile
        ↓
:app:installDebug -> SUCCESS
        ↓
relevant runtime/regression verification -> PASS
        ↓
implementation complete; commits only if explicitly requested
```

Do not claim full runtime verification unless the developer has confirmed it or the relevant tests have actually run.

## Commit messages are architectural labels

The prefix identifies the owner of the file being committed:

```text
app:
core:
feature:
data:
playback:
build:
test:
docs:
```

Use an English imperative description after the prefix. Examples:

```text
core: Add URI-based track observation API
data: Add MediaStore catalog sync state
app: Pass music repository to player sheet
feature: Observe active track catalog metadata
playback: Preserve queue state during track transitions
build: Update Gradle configuration
test: Add catalog synchronization coverage
docs: Update catalog architecture and development rules
```

Prefix ownership:

- `core:` — Core APIs, domain models, and shared UI (`:core:*`).
- `data:` — database/catalog persistence and MediaStore data code (`:data:database`, `:data:media-store`).
- `app:` — application wiring, Activity, root Navigation, theme entry point, and `UnifiedPlayerSheet` (`:app`).
- `feature:` — feature-local UI, routes, state, and ViewModels (`:feature:*`).
- `playback:` — Media3/ExoPlayer implementation (`:playback:media3`).
- `build:` — Gradle and build tooling.
- `test:` — test-only changes.
- `docs:` — engineering documents such as `Agents.md`.

Do not use a prefix merely because it is convenient. Select the prefix matching the file's architectural owner.

## Commit message quality

A message must describe the actual file change, be specific enough to understand without opening the diff, and avoid vague text such as `update`, `fix`, `misc`, `wip`, `final`, or `small changes`.

## Mandatory per-file PowerShell commands

Run these commands from the repository root, repeating them for each changed file:

```powershell
git add -- "path/to/one/file"
git diff --cached --check
git diff --cached --name-only
git commit -m "PREFIX: Specific English description"
```

Before committing, verify that `git diff --cached --name-only` lists exactly the one intended file. If other files are already staged, unstage them and stage only the intended path. Never use `git add .` or `git commit -a` for this workflow.

After each commit, confirm the output and inspect the next file separately. After all intended file commits complete, verify the recent commit history and working tree:

```powershell
git log -N --oneline
git status -sb
```

Replace `N` with the number of commits created. Do not push/sync midway through the series. Run `git push origin main` only when the developer explicitly requests the push.

When the developer asks for commits, report which per-file commits have completed and which remain. If commits were not requested, leave the working tree uncommitted and say so clearly.

---

# 91. PLAYER REGRESSION CHECKLIST

After changing Player, MiniPlayer, or `UnifiedPlayerSheet`, verify:

```text
[ ] real track can be selected
[ ] playback starts
[ ] MiniPlayer appears
[ ] MiniPlayer artwork is correct
[ ] MiniPlayer title/artist are correct
[ ] MiniPlayer previous works
[ ] MiniPlayer play/pause works
[ ] MiniPlayer next works
[ ] tap MiniPlayer opens Player
[ ] swipe MiniPlayer upward opens Player
[ ] swipe Player downward collapses Player
[ ] edge-back on Player predicts collapse
[ ] cancelling edge-back restores Player
[ ] close button collapses Player
[ ] MiniPlayer downward dismissal works
[ ] MiniPlayer dismissal stops playback and clears queue
[ ] tapping/clicking the MiniPlayer area opens the Player through `UnifiedPlayerSheet`
[ ] shared MiniPlayer does not own `onOpenPlayer` or Back handling
[ ] seek tap works
[ ] seek drag works
[ ] previous/next work
[ ] lyrics button works
[ ] fullscreen lyrics replaces the Player artwork content
[ ] fullscreen lyrics Previous/Play/Pause/Next controls work
[ ] fullscreen lyrics seekbar drag/tap works
[ ] fullscreen lyrics elapsed time updates
[ ] fullscreen lyrics seekbar and MiniPlayer use full pill shapes
[ ] fullscreen lyrics bottom spacing is 20dp
[ ] lyrics synchronization follows playback position
[ ] active lyric line scale animation works
[ ] changing tracks while fullscreen lyrics is visible keeps fullscreen mode
[ ] new track lyrics are reloaded while fullscreen mode remains active
[ ] rotating portrait ↔ landscape does not collapse the Player
[ ] rotating while fullscreen lyrics is visible does not exit fullscreen lyrics
[ ] NavigationBar is not touch-intercepting when Player is fully expanded
[ ] portrait layout works
[ ] landscape layout works
```

---

# 92. NAVIGATION REGRESSION CHECKLIST

Verify:

```text
[ ] Home opens
[ ] Search opens
[ ] Library opens
[ ] Settings opens
[ ] Home Recent Play shows no more than 10 preview entries
[ ] Recent Play Artist collection opens and Back returns to Home
[ ] Recent Play Album collection opens and Back returns to Home
[ ] Recent Play Genre collection opens and Back returns to Home
[ ] Collection detail lists only real matching Recent Play records
[ ] Collection play-all/track selection uses the expected real tracks
[ ] Home is root
[ ] Search back behavior works
[ ] Library back behavior works
[ ] Settings back behavior works
[ ] predictive pop transition works
[ ] bottom navigation remains interactive
[ ] MiniPlayer does not intercept NavigationBar touches
[ ] NavigationBar follows system Monet/dynamic color
[ ] NavigationBar does not become pure black on the tested custom ROM
[ ] NavigationBar selected/unselected icons remain theme-aware
```

---

# 92.1. HOME COLLECTION AND LIBRARY SORT REGRESSION CHECKLIST

After changing Home Recent Play collections or Library sorting, verify:

```text
[ ] Home Recent Play preview shows no more than 10 entries
[ ] Recent Play Artist, Album, and Genre collections open their detail pages
[ ] Back from each collection detail returns to Home
[ ] Detail track rows come from persisted Recent Play records for the selected collection
[ ] Play-all / track selection uses the expected real tracks
[ ] Recent Play history is not trimmed to the Home preview size
[ ] Library sort sheet exposes only options allowed for the current browse mode
[ ] All Songs / Folder options: Default, Title, Artist, Album, Genre, Date added, Date modified
[ ] Artist collection options: Default, Artist, Date added, Date modified
[ ] Album collection options: Default, Album, Date added, Date modified
[ ] Genre collection options: Default, Genre, Date added, Date modified
[ ] Songs inside a selected collection support track-level sort options
[ ] Genre sorting uses real genre metadata
[ ] Artist / Album / Genre rows use real artwork and expected shapes when artwork exists
[ ] Duration is not offered as a sort option
[ ] Changing sorting does not trigger a MediaStore rescan
```

---

# 93. LOCALIZATION REGRESSION CHECKLIST

Test both:

```text
English system language
Indonesian system language
```

Verify:

```text
[ ] permission screen
[ ] Home
[ ] Search
[ ] Library
[ ] Player
[ ] MiniPlayer
[ ] Settings
[ ] lyrics labels/states
[ ] empty states
[ ] error states
[ ] accessibility labels
```

Music metadata itself must remain unchanged between languages.

---

# 94. MEDIASTORE REGRESSION CHECKLIST

Test representative local tracks covering:

```text
[ ] normal title
[ ] missing title
[ ] missing artist
[ ] missing album
[ ] missing artwork
[ ] different sample rates
[ ] different bitrates
[ ] different MIME types
[ ] embedded release date/copyright present
[ ] one or both embedded release tags missing
[ ] embedded lyrics
[ ] no embedded lyrics
```

Catalog synchronization checks:

```text
[ ] first sync builds the Room catalog from available MediaStore volumes
[ ] unchanged MediaStore version/generation skips the audio-row catalog query
[ ] generation changes are processed incrementally when the checkpoint remains valid
[ ] changed MediaStore version or invalid checkpoint triggers a full scan
[ ] removed MediaStore track IDs are reconciled out of Room
[ ] an unavailable volume is not treated as an empty volume
[ ] catalog listing does not open every audio file through MediaMetadataRetriever
[ ] selected embedded metadata is enriched separately in bounded batches
[ ] Room-backed Home, Library, and Search show the synchronized catalog
[ ] active Player Track Info observes catalog metadata by URI without restarting playback
[ ] Recent Play metadata is refreshed by URI when embedded release metadata is discovered later
```

No metadata should be fabricated. Missing embedded copyright/release-date tags are valid absent values and must not be displayed as synthetic metadata.

---

# 95. LYRIC REGRESSION CHECKLIST

Test:

```text
[ ] no lyrics
[ ] line-synced lyrics
[ ] word-synced lyrics
[ ] metadata/header lines
[ ] unusual spacing
[ ] pause/resume while lyrics are visible
[ ] seek while lyrics are visible
[ ] track change while lyrics are visible
[ ] automatic active-line scrolling
[ ] active-word highlighting
```

---

# 96. RESOURCE NAMESPACE RULE

When a Kotlin file belongs to a feature module and also needs resources from `core:ui`, keep the namespaces explicit.

Example:

```kotlin
import com.rovia.music.core.ui.R as CoreUiR
```

Then:

```kotlin
CoreUiR.drawable.ic_pause
```

Feature-local strings should remain in the feature module's `R` namespace.

Do not relocate resources purely to remove namespace qualification.

---

# 97. ICON RESOURCE RULE

Use local Android Vector Drawable resources for UI icons where the project already does so.

Do not use emoji as icon replacements.

Do not add a full icon pack for one or two new icons.

---

# 98. SOURCE OF TRUTH RULE

This `Agents.md` documents the intended/current architecture, but the code is the final runtime authority.

If code and this file genuinely disagree because a new feature was intentionally implemented:

1. inspect the code;
2. verify the new behavior;
3. update `Agents.md` together with the intentional architectural change.

Do not silently rewrite working code merely to make it match stale documentation.

---

# 99. CHANGE DOCUMENTATION EXPECTATION

When a meaningful architecture or interaction change is intentionally made, document:

```text
what changed
why it changed
which module owns it
which existing behavior must remain
how it is verified
```

For Player/gesture/navigation changes, explicitly document gesture direction, threshold, progress mapping, and back behavior.

For localization changes, document resource ownership and the system-language behavior.

For playback changes, document queue semantics and Media3 state flow.

---

# 99.1. CURRENT ENGINEERING BASELINE

The current baseline includes:

```text
Material 3 Expressive UI
native TopAppBar headers
native ButtonGroup controls
native ListItem rows
tappable folder breadcrumb/path
MediaStore-derived folder browsing
context-aware Library sorting with a mode-specific option matrix
Artist / Album / Genre collection artwork and sorting
persistent distinct-URI Recent Play history without a hard 10-row cap
Home Recent Play preview limited to 10 entries
Home Recent Play collection detail pages for Artist / Album / Genre
Room-backed synchronized music catalog
per-volume MediaStore generation/version synchronization
separate bounded embedded-metadata enrichment
URI-based active-player metadata observation
explicit dp geometry contracts
Kotlin-only source tree
no src/main/java directories
```

Any future change must preserve these as the current architecture unless the developer explicitly
requests a redesign.

---

# 100. STOP CONDITION

The current product milestone is complete when the existing application compiles and the already-implemented behaviors remain intact.

An AI agent must not automatically continue into future roadmap features after reaching a successful build.

The correct sequence is:

```text
understand
    ↓
preserve
    ↓
change only requested behavior
    ↓
compile
    ↓
run relevant regression tests
    ↓
document intentional architecture changes
    ↓
stop
```

---

# 101. ROVIA IS NOT A GENERIC TEMPLATE

Do not treat Rovia like a standard starter project and "clean it up" by:

- replacing Navigation 3;
- creating a conventional `NavHost` for Player;
- moving all strings to `app`;
- introducing Hilt;
- introducing another database/persistence framework beside the existing Room 3 database;
- installing Coil;
- installing an icon library;
- installing FFmpeg;
- creating a generic BaseScreen;
- creating a generic BaseViewModel;
- creating a universal component layer;
- recreating the player from scratch.

The existing architecture is deliberately explicit and small.

---

# 102. FINAL IMPLEMENTATION PRINCIPLE

Rovia should remain:

```text
small dependency tree
clear module boundaries
real Android APIs
real Media3 playback
real device data
Room 3 for application state and the synchronized local music catalog
MediaStore as the authority for underlying local media and indexed source values
per-volume version/generation synchronization with incremental updates when possible
bounded embedded-metadata enrichment separate from catalog listing
current development schema version stays at 1 unless a migration is explicitly requested
real local lyrics
real system localization
real predictive back
real Material 3 Expressive UI
no fake data
no speculative infrastructure
no hidden persistence
maintainable Kotlin
maintainable Compose
```

When uncertain:

> **Preserve the current mechanism first. Change it only when the requested feature truly requires the change.**

When a feature does not exist:

> **Do not fake it.**

When Android or AndroidX already provides the capability:

> **Prefer the existing official API instead of introducing another dependency.**

When the requested milestone is complete:

> **Stop and wait for the developer's next instruction.**
---

# 103. UI GEOMETRY AND SPACING CONTRACT

UI positioning is now treated as an explicit engineering contract.

The project uses Compose `dp` values for layout geometry. Do not replace these values with arbitrary
per-device pixel measurements.

Important rule:

```text
dp = layout contract
px = density-dependent rendering result
```

Raw pixel constants must not be used as a substitute for `dp` in Compose layout code unless a
platform API explicitly requires pixels.

Current verified geometry values include:

```text
Library / Settings / Folder Filter TopAppBar outer horizontal padding = 4dp

Library All Songs / Folders ButtonGroup spacing = 8dp

TrackRow album artwork size = 56dp
TrackRow artwork slot = 56dp

Library / Search / Home bottom content padding:
    with MiniPlayer    = 200dp
    without MiniPlayer = 100dp

MiniPlayer top corner radius    = 32dp
MiniPlayer bottom corner radius = 20dp

RoviaNavigationBar height = 80dp
NavigationBar bottom padding = 20dp
MiniPlayerNavigationSpacing = 8dp
```

These values exist to prevent recurring alignment drift between screens.

When modifying one of these surfaces:

1. preserve the documented value unless the design is intentionally changed;
2. change all dependent layout calculations together;
3. compile immediately;
4. test on the real Android device;
5. update `Agents.md` when the intentional geometry changes.

Do not "eyeball" a replacement such as `6dp`, `10dp`, `16dp`, or `24dp` merely because it looks close.

---

# 104. TOP APP BAR CONTRACT

Rovia now consistently uses the native Material 3 `TopAppBar` on the screens that require a
persistent page header.

Current implementations include:

```text
LibraryScreen
SettingsScreen
FolderFilterScreen
```

Header rules:

```text
TopAppBar owns the header structure.
navigationIcon slot owns back navigation.
actions slot owns trailing screen actions.
```

The top-level outer horizontal padding currently used by these headers is:

```text
4dp
```

Do not rebuild these headers with a manual `Row` merely to position the back button and action icon.

Do not add a second ad-hoc status-bar spacer when the `TopAppBar` already handles the relevant
window insets.

When a new action is added, use the existing `TopAppBar.actions` slot rather than creating a second
horizontal layout beside the app bar.

This is the current alignment contract for Settings and Folder Filter so their header spacing does not
drift apart from Library.

---

# 105. LIBRARY CONTROLS AND BUTTON GROUP CONTRACT

The Library mode selector uses Material 3 Expressive `ButtonGroup`.

Current pattern:

```text
ButtonGroup
    ├── All Songs
    ├── Folders
    └── OverflowIndicator where required by the current API
```

The current verified spacing value for the selector is:

```text
8dp
```

The labels are localized through `stringResource`.

Do not:

```text
build a fake segmented control from arbitrary Cards
insert an unrelated custom Row gap
hardcode English labels
replace ButtonGroup with a home-made shape system
```

The breadcrumb/path navigation also uses Material 3 Expressive `ButtonGroup` semantics where the
current implementation groups path segments.

Path segments must remain actual interactive items.

---

# 106. LIST ITEM AND SHAPE CONTRACT

Current Library list rows intentionally use native Material 3 `ListItem`.

`TrackRow` uses:

```text
ListItem
ListItemDefaults.shapes()
AlbumArtwork
```

Track artwork size:

```text
56dp × 56dp
```

The folder row also uses native `ListItem` and `ListItemDefaults.shapes()`.

Do not replace these rows with custom rounded `Row` + `clickable` constructions unless the official
Material component can no longer satisfy a real requirement.

Shape handling should prefer:

```text
ListItemDefaults.shapes()
IconButtonDefaults.shapes()
ButtonDefaults.shapes()
```

or the relevant official Material 3 Expressive shape API.

Do not freeze expressive controls to a single arbitrary shape when a native morphing/default API
already provides the correct interaction behavior.

---

# 107. MINIPLAYER SHAPE AND CONTROL CONTRACT

`MiniPlayer` uses a deliberate custom container shape:

```text
topStart     = 32dp
topEnd       = 32dp
bottomStart  = 20dp
bottomEnd    = 20dp
```

This asymmetric container shape is intentional and is not to be replaced with an arbitrary standard
rounded rectangle.

Inside the MiniPlayer, the playback controls use the official Material 3 Expressive APIs.

Current control shape policy:

```text
IconButtonDefaults.shapes()
FilledIconButton
ButtonGroup
animateWidth()
```

Do not reintroduce manually frozen button shapes where the native Expressive shape API is already used.

---

# 108. LATEST PLAYER / MINIPLAYER REFINEMENT CONTRACT

This section records the current Player architecture after the latest verified refinement pass.

## 108.1. Shared MiniPlayer API

The reusable `:core:ui` `MiniPlayer` no longer owns an `onOpenPlayer` callback.

Current responsibilities:

```text
previous
play/pause
next
render playback content
```

Player opening is coordinated by `UnifiedPlayerSheet`.

Do not reintroduce navigation ownership into the shared MiniPlayer just to make it clickable in one
specific context.

## 108.2. UnifiedPlayerSheet state preservation

`UnifiedPlayerSheet` stores its sheet progress with `rememberSaveable`.

Therefore:

```text
portrait
    ↕
landscape
```

rotation does not implicitly change:

```text
expanded Player
    -> collapsed MiniPlayer
```

The restored progress is propagated through `onProgressChanged` after restoration so top-level
navigation can immediately reflect the restored Player state.

## 108.3. Fully expanded NavigationBar behavior

At:

```text
progress >= 0.999
```

the NavigationBar composable is removed from the active composition instead of merely receiving
`alpha = 0`.

This is required to prevent an invisible bottom layer from intercepting Player control touches.

The NavigationBar remains visible/interactive while the Player is sufficiently collapsed to show the
bottom chrome.

## 108.4. Fullscreen lyrics architecture

When `isLyricsVisible` is true, `PlayerScreen` renders:

```text
PlayerLyricsFullscreenContent
    ├── PlayerActionButtonGroup
    ├── PlayerLyric / LyricViewer
    └── PlayerLyricsMiniPlayer
        ├── dedicated elapsed-time + WavySeekBar pill
        └── dedicated playback MiniPlayer pill
```

This fullscreen MiniPlayer is intentionally separate from the reusable global `MiniPlayer`.

The dedicated fullscreen bottom controls directly call the same:

```text
onPrevious
onPlayPause
onNext
onSeek
```

callbacks supplied by `PlayerRoute`, so there is no duplicate playback architecture.

## 108.5. Fullscreen lyrics pill geometry

The dedicated fullscreen lyrics bottom controls use:

```text
seekbar container
    = RoundedCornerShape(percent = 50)

MiniPlayer container
    = RoundedCornerShape(percent = 50)

outer gap
    = 8dp

seekbar horizontal inner padding
    = 16dp

seekbar vertical inner padding
    = 8dp

elapsed-time ↔ WavySeekBar gap
    = 10dp

fullscreen content bottom padding
    = 20dp
```

The seekbar is intentionally shorter than the outer pill width because the elapsed-time label and
horizontal internal padding reserve visible space on the left and right.

The two bottom surfaces use the same full-pill geometry so they read as one consistent control family.

## 108.6. Fullscreen lyrics track-change behavior

Track changes while fullscreen lyrics is visible must behave as:

```text
Previous / Next
      ↓
current track changes
      ↓
old lyric content cleared
      ↓
new embedded lyrics loaded
      ↓
fullscreen lyrics remains visible
```

Do not automatically hide the fullscreen lyrics view just because the current track changed.

Closing the Player remains an explicit operation and hides lyrics before collapsing the sheet.

## 108.7. Player orientation behavior

The Player keeps dedicated portrait and landscape layouts.

The current portrait layout preserves a fixed artwork slot so artwork size changes do not unexpectedly
move the content below it.

The current landscape layout remains the established two-column composition and must not be altered
during unrelated fullscreen-lyrics work.

Orientation-specific behavior belongs in:

```text
PlayerPortraitContent
PlayerLandscapeContent
```

not in `UnifiedPlayerSheet`.

## 108.8. Recent verified commits

The latest three Player interaction refinements were intentionally separated by module ownership:

```text
cb999ff core: Clean up MiniPlayer interaction
b4c5dc8 app: Refine UnifiedPlayerSheet behavior
95cb760 feature: Refine fullscreen lyrics player
```

This commit separation is the preferred pattern for future changes:

```text
core:*     -> shared reusable UI/API behavior
app:*      -> application shell, sheet, navigation, composition
feature:*  -> feature-local presentation and behavior
```

Do not combine unrelated module ownership changes into one commit when they can be verified independently.

---

# 109. FOLDER FILTER HEADER CONTRACT

`FolderFilterScreen` follows the same header geometry rules as Settings.

Current header:

```text
TopAppBar
    navigationIcon -> Back
    actions        -> Add
```

Outer horizontal padding:

```text
4dp
```

The header actions use native Material 3 / Expressive icon-button shape defaults.

The folder list uses native `ListItem` shape defaults.

The add action uses the current official Material 3 button/shape API rather than a hand-built custom
container.

The purpose of this contract is visual consistency:

```text
Library header
    ↕
Settings header
    ↕
Folder Filter header
```

All three should keep the same horizontal alignment logic rather than independently tuning their
back/action positions.

---

# 110. KOTLIN-ONLY SOURCE TREE CONTRACT

The Rovia source migration is complete.

The enforced current rule is:

```text
ALL *.kt source files
    ↓
src/main/kotlin/
```

and:

```text
src/main/java/
    ↓
must not exist in the current application modules
```

There are currently no Java source files in:

```text
app
core/*
data/*
playback/*
feature/*
```

This was intentionally migrated module-by-module without changing package declarations or source
behavior.

Do not recreate `src/main/java` solely because a generated Android/Compose template uses that
directory.

When adding a new Kotlin file, place it under the owning module's:

```text
src/main/kotlin/<package>/
```

Keep the package declaration unchanged unless the developer explicitly requests a package refactor.

---

# 111. SOURCE TREE MIGRATION RECORD

The Kotlin source migration was completed in this sequence:

```text
core:model         -> src/main/kotlin
core:library-api   -> src/main/kotlin
core:playback-api  -> src/main/kotlin
core:ui            -> src/main/kotlin
data:media-store   -> src/main/kotlin
playback:media3    -> src/main/kotlin
feature:home       -> src/main/kotlin
feature:search     -> src/main/kotlin
feature:library    -> src/main/kotlin
feature:player     -> src/main/kotlin
feature:settings   -> src/main/kotlin
app                -> src/main/kotlin
```

Each module was verified after moving its `.kt` files.

The validation sequence was intentionally:

```text
inspect actual source tree
    ↓
create src/main/kotlin package directory
    ↓
move only existing *.kt files
    ↓
verify no *.kt remains under src/main/java
    ↓
compile
    ↓
remove empty src/main/java
    ↓
compile again
```

No package names were changed by this migration.

No resources were moved from `src/main/res`.

No module dependency direction was changed.

No feature behavior was intentionally changed by the source-tree migration.

---

# 112. FINAL SOURCE-TREE AUDIT CONTRACT

Before declaring a source-tree migration complete, verify:

```powershell
Get-ChildItem -Path ".\app", ".\core", ".\data", ".\playback", ".\feature" `
    -Recurse -Filter "*.kt" -File -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -match "\src\main\java\" } |
    Select-Object -ExpandProperty FullName
```

Expected result:

```text
<empty>
```

Then:

```powershell
Get-ChildItem -Path ".\app", ".\core", ".\data", ".\playback", ".\feature" `
    -Recurse -Directory -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -match "\src\main\java$" } |
    Select-Object -ExpandProperty FullName
```

Expected result:

```text
<empty>
```

Then:

```powershell
Get-ChildItem -Path ".\app", ".\core", ".\data", ".\playback", ".\feature" `
    -Recurse -Filter "*.java" -File -ErrorAction SilentlyContinue |
    Select-Object -ExpandProperty FullName
```

Expected result:

```text
<empty>
```

The presence of Gradle tasks such as:

```text
javaPreCompileDebug
compileDebugJavaWithJavac NO-SOURCE
```

is not evidence that Java source files exist. Those tasks are part of the Android build pipeline.

The source audit, not the task name alone, determines whether Java source is present.

---

# 113. UI ALIGNMENT REGRESSION CHECKLIST

Whenever Library, Settings, Folder Filter, or shared playback UI spacing is modified, verify:

```text
[ ] TopAppBar is used where the current screen contract requires it
[ ] TopAppBar horizontal outer padding remains 4dp
[ ] Back action sits in the navigationIcon slot
[ ] trailing actions sit in the actions slot
[ ] no duplicate manual status-bar spacer was added
[ ] Library All Songs/Folders uses ButtonGroup
[ ] Library selector gap remains 8dp
[ ] Library folder breadcrumb/path remains tappable
[ ] folder navigation does not trigger a MediaStore/filesystem rescan
[ ] TrackRow artwork remains 56dp
[ ] FolderRow uses native ListItem shape defaults
[ ] Library / Search / Home bottom padding is 200dp with MiniPlayer
[ ] Library / Search / Home bottom padding is 100dp without MiniPlayer
[ ] MiniPlayer top radius remains 32dp
[ ] MiniPlayer bottom radius remains 20dp
[ ] NavigationBar spacing contract remains intact
```

Do not solve alignment problems by introducing a new arbitrary value before checking which existing
contract was violated.

---

# 114. CHANGE LOG: CURRENT UI AND ARCHITECTURE REFINEMENT

This engineering record documents the following intentional changes made during the latest
implementation pass.

## UI spacing and positioning

- Re-aligned page headers around native `TopAppBar`.
- Standardized outer header horizontal padding at `4dp`.
- Removed the need for manually rebuilt header rows where `TopAppBar` already provides the correct
  navigation/action slots.
- Standardized Library mode-selector spacing at `8dp`.
- Standardized TrackRow artwork dimensions at `56dp`.
- Standardized Library / Search / Home bottom content padding around MiniPlayer presence:
  `200dp` with MiniPlayer and `100dp` without MiniPlayer.
- Preserved the asymmetric MiniPlayer container shape at `32dp` top and `20dp` bottom radii.
- Current navigation geometry uses an 80dp NavigationBar, 20dp bottom padding, and 8dp
  MiniPlayer-to-NavigationBar spacing.

## Material 3 Expressive implementation

- Library mode switching uses `ButtonGroup`.
- Library and folder rows use native `ListItem`.
- Shape behavior uses official `ListItemDefaults`, `IconButtonDefaults`, and relevant Material 3
  Expressive shape APIs.
- MiniPlayer controls use native `IconButtonDefaults.shapes()` behavior rather than frozen manual
  shapes.
- Existing Monet/dynamic-color behavior remains the source of truth.

## Folder navigation

- Added explicit All Songs / Folders Library mode.
- Added MediaStore-derived folder browsing.
- Added parent-folder navigation.
- Added a tappable breadcrumb/navigation path.
- Added normalized folder-path handling in Library state.
- Preserved the no-rescan rule for folder filtering/navigation.
- Added animated folder Back-icon appearance/disappearance inside the native `TopAppBar.navigationIcon`
  slot so the Library title shifts continuously with the changing icon-slot width.
- Preserved the native `TopAppBar` rather than rebuilding the header with a manual row or title offset.

## Navigation 3 and Predictive Back

- Reworked Home, Search, and Library as equal top-level sibling destinations.
- Removed the previous Home-root Back-stack relationship between those three tabs.
- Settings is a child of the active top-level destination.
- Folder Filter and About are children of Settings.
- Removed destination enter animations from Navigation 3.
- Removed normal/programmatic destination pop animations.
- Kept Predictive Back as the only destination-level navigation motion.
- Predictive Back now scales the current opaque destination surface toward `90%` and moves it to the
  right without fade.
- Added `NavigationDestinationSurface` around every active Navigation 3 destination so the entire page
  surface participates in Predictive Back.
- Kept normal Back button presses instantaneous; the predictive scale/slide motion is exclusive to the
  gesture-driven `predictivePopTransitionSpec`.
- Established a strict rule that future Navigation 3 features must not introduce new page transitions,
  fades, entrance animations, shared-element transitions, or arbitrary destination motion.

## Source architecture

- Migrated every current Kotlin source file from `src/main/java` to `src/main/kotlin`.
- Removed the old `src/main/java` directories after successful compile checkpoints.
- Kept package declarations unchanged.
- Kept resources in `src/main/res`.
- Confirmed there are no remaining `.kt` files under `src/main/java`.
- Confirmed there are no remaining `.java` source files in the current application modules.

## Player feature modularization

- Refactored the 1,956-line `PlayerScreen.kt` into focused Player presentation files without
  changing the Player state flow, playback callbacks, orientation behavior, lyrics behavior, seek
  interaction, or Material 3 Expressive controls.
- Kept transient seek interaction state in `PlayerScreen`, because it belongs to the screen-level
  gesture/session interaction rather than persistent or domain state.
- Moved portrait and landscape compositions into separate files so future orientation-specific UI
  changes remain localized.
- Isolated artwork/lyrics presentation, top action controls, playback controls, the custom wavy seek
  bar, and technical/time metadata into focused internal presentation components.
- Kept these components inside `:feature:player`; no new module, dependency, DI framework, or data
  abstraction was introduced.
- Kept cross-file Player UI helpers `internal` so they remain module-local rather than becoming a
  public feature API.
- Preserved the existing `UnifiedPlayerSheet`, `PlayerRoute`, `PlayerViewModel`, `LyricViewer`,
  Predictive Back behavior, and playback architecture unchanged.

## Latest Player interaction refinement

- Reworked `UnifiedPlayerSheet` into a true MiniPlayer ↔ Player morphing surface using one physical
  `Surface` driven by normalized `progress`.
- Kept the Player out of Navigation 3 and out of `SharedTransitionLayout`.
- Added saveable Player progress so rotation does not collapse the Player.
- Propagated restored sheet progress back to `MainNavigation`.
- Removed the invisible NavigationBar touch-interception problem by composing NavigationBar only while
  the Player is not fully expanded.
- Cleaned the shared `MiniPlayer` API so it no longer owns `onOpenPlayer`; opening behavior remains in
  `UnifiedPlayerSheet`.
- Added a dedicated `PlayerLyricsMiniPlayer` for fullscreen lyrics instead of reusing the shared
  `MiniPlayer` component in a context with different interaction requirements.
- Split fullscreen lyrics bottom controls into two physical pill surfaces:
  a dedicated seekbar surface and a dedicated MiniPlayer surface.
- Added elapsed playback time to the left side of the fullscreen lyrics seekbar.
- Added internal horizontal seekbar spacing so the wavy seek visual does not touch the pill edges.
- Standardized fullscreen lyrics seekbar and MiniPlayer shapes to full-pill geometry.
- Matched fullscreen lyrics bottom content padding to the NavigationBar bottom padding of `20dp`.
- Preserved direct Previous / Play-Pause / Next callbacks in fullscreen lyrics.
- Kept fullscreen lyrics visible across track changes while clearing/reloading the actual lyric content.
- Kept the explicit Player close operation responsible for hiding lyrics.
- Added smoother lyric active-line scaling while preserving adaptive sizing for long lines.
- Preserved the established portrait and landscape Player layouts while extracting the fullscreen
  lyrics-specific presentation into focused feature-local files.
- Kept all changes within the existing `:app`, `:core:ui`, and `:feature:player` module boundaries;
  no additional dependency or architecture layer was introduced.

## Recent Play history and collection pages

- Removed the old database-side trim that capped Recent Play at 10 records. `recent_plays` retains one latest-played record per distinct content URI; replaying the same URI updates its recency instead of creating duplicate rows for that URI.
- Kept the Home Recent Play preview bounded to 10 entries as a UI-only performance/presentation rule.
- Added Home collection grouping for Artist, Album, and Genre, using real metadata from persisted Recent Play records.
- Added the `RecentPlayCollection` model layer and dedicated `RecentCollectionRoute` / `RecentCollectionScreen` implementation in `:feature:home`.
- Added Navigation 3 keys and destinations for the collection detail flow while keeping Home, Search, and Library as the only bottom-navigation siblings.
- Collection pages display actual matching Recent Play tracks and reuse the established native Material 3 / `TrackRow` presentation. Empty or missing values must not be replaced with invented entries.

## Library sorting and collection artwork

- Added `LibrarySort.kt` and `LibrarySortBottomSheet.kt` to `:feature:library`.
- Made visible sort options depend on the current browsing context instead of showing one universal list.
- Added actual Genre-based sorting and removed Duration from the sort menu.
- Added/retained type-appropriate artwork for Artist, Album, and Genre collection entries, using the existing visual contracts and Material 3 Expressive shape behavior.

## Workflow correction

- Build checkpoints apply after application source/code changes; documentation-only Markdown edits do not require Gradle compilation or APK installation.
- Git commits are not automatic. Commit one file at a time only when explicitly requested; never stage the whole tree or push without an explicit request.

## Database and synchronized catalog integration

- Room 3 remains the database layer in `:data:database`, backed by Android framework SQLite.
- Current schema version remains `1` during active development; the exported `1.json` is kept aligned with
  `RoviaDatabase.kt` and its entities.
- Persisted tables are `music_tracks`, `media_store_sync_state`, `recent_plays`, and `excluded_folders`.
- `music_tracks` is the synchronized local audio catalog used by Home, Library, Search, and active-player
  metadata observation. Its composite key is `(volume_name, track_id)`.
- `media_store_sync_state` stores per-volume MediaStore version/generation checkpoints.
- `recent_plays` is keyed by content URI, includes real track metadata fields when available, and retains one latest-played record per distinct URI without a hard 10-row cap.
- The Home Recent Play preview is limited to 10 entries; Artist/Album/Genre collection groups and detail track lists derive from the persisted history.
- Library sorting is context-aware: All Songs/Folder and selected-collection track lists support Default, Title, Artist, Album, Genre, Date added, and Date modified; Artist/Album/Genre collection lists expose their mode-specific subsets. Duration is not a sort option.
- `excluded_folders` remains the persistent store for Folder Filter selections.
- `AppContainer` wires `MusicRepository` to `RoomMusicCatalogRepository`, not to the legacy
  `MediaStoreMusicRepository` implementation.
- `MediaStoreCatalogDataSource` queries available volumes, source version/generation, indexed audio rows,
  and lightweight IDs for reconciliation. It does not open each audio file during catalog listing.
- `MediaCatalogSyncCoordinator` performs full sync for first/invalidated checkpoints, incremental sync when
  generation advances, skips unchanged volume scans, and reconciles removed track IDs. An unavailable volume
  is not treated as an empty catalog.
- `EmbeddedMetadataEnrichmentProcessor` separately extracts selected embedded tags in bounded batches
  (currently 24). It records a source signature and processing status so completed/failed sources are not
  blindly reopened on every startup. Missing embedded tags remain missing values.
- Embedded `releaseDate` and `copyright` updates also refresh an existing Recent Play snapshot by URI.
- `PlayerViewModel` observes the active track's catalog metadata by URI. The UI uses the refreshed `Track` for
  presentation without restarting playback or rebuilding the Media3 queue; playback controls/state remain
  sourced from `PlaybackController`.
- `MusicRepository` exposes snapshot compatibility methods plus `observeRecentlyAdded`, `observeAllTracks`,
  and `observeTrackByUri`; the Room implementation supplies reactive catalog Flows and a URI-scoped lookup.
- Artwork stays on the existing Android thumbnail path. `artworkUri` is not altered by embedded metadata
  enrichment; artwork/cache behavior should only be changed for a separately verified artwork issue.

### Current development schema policy

During active development, keep `RoviaDatabase` at version `1` and do not add migrations/automigrations unless
explicitly requested. When incompatible schema changes require a clean local test database, clearing app data
or reinstalling is acceptable. This is a development convenience, not a policy to discard real users' data on
production upgrades. Before shipping schema evolution to existing users, define the released baseline and add
migrations/tests that preserve their data. Do not introduce destructive fallback migrations as a production
recovery strategy.

---

## Navigation contract verification

The current navigation contract is:

```text
Home / Search / Library
    = top-level siblings

RecentCollection detail (Artist / Album / Genre)
    = child route opened from Home's recent collection sections

Settings
    = child of active top-level destination

FolderFilter / About
    = children of Settings

Forward navigation
    = instantaneous

Normal Back button
    = instantaneous

Predictive Back gesture
    = surface scales to 90%
    = moves right
    = no fade
```

Future navigation work must keep this contract and update the Navigation 3 sections of `Agents.md` whenever
the implementation intentionally changes.

## Verification status for the current catalog work

Verified during the current implementation session:

```text
:app:assembleDebug -> BUILD SUCCESSFUL after the catalog and active-player metadata wiring changes
Track Info releaseDate -> developer confirmed it appears for tracks that have the embedded tag
Missing copyright tag -> valid missing metadata; do not fabricate a value
Git history -> catalog changes split into one-file commits by module prefix
Git working-tree and ahead/behind status are transient. Check `git status -sb` in the live repository instead of relying on a previously recorded status.
```

A missing copyright value is expected when the source file does not contain that tag. It is not by itself a
processing failure. Artwork code was not changed as part of embedded metadata enrichment; a separate runtime
artwork regression should be investigated only if an actual artwork issue is observed.

Do not claim that the latest full catalog schema has passed `connectedDebugAndroidTest` or Release verification
unless those commands have actually been run against this exact schema and build. The instrumentation test
must be kept aligned with the current table set, and it must use a dedicated test database rather than the
developer's real `rovia.db`.

The existing source-tree migration rules remain unchanged. The current catalog integration intentionally adds
a synchronized Room-backed index while keeping MediaStore authoritative for the underlying local media.
Git commits are created only when the developer explicitly requests them; when requested, use one file per commit and do not push unless explicitly asked.
