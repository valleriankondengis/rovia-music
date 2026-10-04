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
- Home screen.
- Recently Added section, limited to 10 real tracks.
- Persistent Recent Play history, limited to 10 tracks.
- Library screen containing the complete local audio collection.
- Library All Songs / Folders mode selector using Material 3 Expressive `ButtonGroup`.
- MediaStore-derived folder browsing without triggering filesystem/media rescans.
- Tapable folder navigation path / breadcrumb for moving between root and nested folders.
- Folder parent navigation and explicit root/all-songs state handling.
- Folder Filter UI with native Material 3 controls and an add action, with persistent exclusion selections.
- Search screen with realtime local search and fuzzy matching.
- Real playback through Media3/ExoPlayer.
- In-memory playback queue.
- Play, pause, resume, seek, previous, and next.
- Global MiniPlayer while a track is active.
- Unified expandable Player sheet.
- Swipe up from MiniPlayer to open Player.
- Swipe down from Player to collapse Player.
- MiniPlayer downward dismissal gesture that stops playback and clears the queue when the dismissal threshold is reached.
- Android Predictive Back for the Player sheet.
- Navigation 3 Predictive Back for normal destinations, with no forward or normal-pop destination animation.
- MediaSession background playback and system media controls.
- Real MediaStore artwork loading through Android thumbnails.
- Audio technical information such as sample rate, bitrate, and MIME-derived format when metadata exists.
- Embedded local lyrics parsing.
- Line-synced and word-synced lyrics.
- Automatic lyric scrolling and active-word highlighting.
- Player artwork/lyrics toggle.
- Portrait and landscape Player layouts.
- Material 3 Expressive controls and motion.
- System dynamic color / Monet.
- English and Indonesian localization through Android resources.
- A Settings destination shell containing a title and back action.

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

## 3.2 Application database

Rovia uses **Room 3 as the abstraction layer over Android SQLite** for structured application-owned
persistent state.

The database implementation is isolated in:

```text
:data:database
```

Current persistent data:

- Recent Play;
- Folder Filter selections.

Do not introduce another database technology for application-owned structured state when the existing
Rovia database can support the requirement.

Room/SQLite implementation details must remain inside `:data:database`. Feature modules must use
Core repository interfaces rather than accessing Room, DAO, Entity, or SQLite classes directly.

MediaStore remains the source of truth for the device's local audio collection. Rovia's database must
not become a mirror of the complete MediaStore library.

Do not add `DataStore` or `SharedPreferences` as an alternative persistence mechanism for data that
belongs in the existing structured application database.

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

Current version catalog baseline:

```text
Android Gradle Plugin : 9.4.0
Kotlin                : 2.4.20
Compose BOM           : 2026.09.00
Material 3            : 1.5.0-alpha29
Navigation 3          : 1.2.0
Media3                : 1.11.1
Coroutines            : 1.10.2
Room 3                : 3.0.3
AndroidX SQLite       : 2.7.1
KSP                   : 2.3.12
JDK                   : 17
Java source/target    : 17
compileSdk            : 37
minSdk                : 36
targetSdk             : 36
```

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
│   │   │   │   └── RecentPlayDao.kt
│   │   │   ├── entity/
│   │   │   │   ├── ExcludedFolderEntity.kt
│   │   │   │   └── RecentPlayEntity.kt
│   │   │   ├── mapper/
│   │   │   │   └── RecentPlayEntityMapper.kt
│   │   │   └── repository/
│   │   │       ├── RoomFolderFilterRepository.kt
│   │   │       └── RoomRecentPlayRepository.kt
│   │   └── src/androidTest/kotlin/com/rovia/music/data/database/
│   │       └── RoviaDatabaseMigrationTest.kt
│   │
│   └── media-store/
│       └── src/main/kotlin/com/rovia/music/data/media/store/
│           ├── EmbeddedLyricsRepository.kt
│           ├── MediaStoreFolderBrowserRepository.kt
│           ├── MediaStoreFolderScannerRepository.kt
│           └── MediaStoreMusicRepository.kt
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
    │       └── HomeViewModelFactory.kt
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
    │       ├── LibraryUiState.kt
    │       ├── LibraryViewModel.kt
    │       └── LibraryViewModelFactory.kt
    │
    ├── player/
    │   └── src/main/kotlin/com/rovia/music/feature/player/
    │       ├── LyricViewer.kt
    │       ├── PlayerRoute.kt
    │       ├── PlayerScreen.kt
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
recent_plays
excluded_folders
```

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

Owns Home UI and Home ViewModel state.

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

`AppContainer` currently wires concrete implementations:

```text
MusicRepository
    -> MediaStoreMusicRepository

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

Conceptually:

```text
RoviaApplication
     │
     ├── applicationScope
     │
     ▼
 AppContainer
      ├── MusicRepository
      ├── FolderScannerRepository
      ├── FolderFilterRepository
      ├── LyricsRepository
      ├── RecentPlayRepository
      └── PlaybackController
```

Room and SQLite implementation details are hidden behind the `:data:database` module.

Keep this explicit.

## 10.1 DATABASE ARCHITECTURE

Rovia uses Room 3 as an abstraction layer over SQLite.

The database is isolated in:

```text
:data:database
```

Current implementation:

```text
Room 3
   ↓
RoviaDatabase
   ↓
AndroidSQLiteDriver
   ↓
Android framework SQLite
   ↓
rovia.db
```

Current database tables:

### `recent_plays`

Stores persistent Recent Play state.

Primary key:

```text
track_id
```

Stored fields:

```text
track_id
uri
title
artist
album
duration_ms
date_added_epoch_seconds
mime_type
sample_rate_hz
bitrate_bps
artwork_uri
last_played_at_epoch_millis
```

Recent Play is limited to the latest 10 records.

### `excluded_folders`

Stores the user's Folder Filter selections.

Primary key:

```text
relative_path
```

The database stores only the user's exclusion decision. Folder metadata and local audio discovery
remain MediaStore responsibilities.

### Database boundaries

The intended dependency direction is:

```text
feature / playback
        ↓
Core repository interface
        ↓
:data:database
        ↓
Room 3
        ↓
SQLite
```

Feature modules must not access:

```text
RoomDatabase
DAO
Entity
SQLiteDatabase
SQLiteOpenHelper
```

directly.

### MediaStore remains the source of truth

The Rovia database must not mirror the complete MediaStore collection.

MediaStore remains authoritative for:

```text
audio files
audio metadata
MediaStore IDs
content URIs
relative folder paths
device media availability
```

SQLite is only for application-owned state that must survive process/application restarts.

### Initialization

The database is created once for the application process and shared through `RoviaDatabaseProvider`.

Folder Filter state is restored from Room before MediaStore-backed library queries use the exclusion set.

### Schema versioning

The current database schema version is:

```text
1
```

Version 1 contains:

```text
recent_plays
excluded_folders
```

The exported baseline schema is stored at:

```text
data/database/schemas/com.rovia.music.data.database.RoviaDatabase/1.json
```

The schema export is part of the source-controlled database history. Do not overwrite an older schema
file with a newer schema.

Future versions are additive/evolutionary and must preserve existing user data.

### Migration policy

Rovia uses incremental Room migrations.

The expected evolution pattern is:

```text
Version 1
    ↓ migration
Version 2
    ↓ migration
Version 3
    ↓ migration
Version 4
```

A migration version represents a database schema change, not necessarily one feature.

For example, Favorites, Playlists, and Play History may be introduced together as one schema version if
their final schema is designed and shipped together.

When a future feature changes the database schema:

1. Add or modify the relevant Room `Entity`/schema.
2. Increase the `RoviaDatabase` version.
3. Create the required migration path from the previous schema version.
4. Generate the new exported schema.
5. Keep all previous exported schema files.
6. Add or update migration tests.
7. Verify that existing user data remains available after migration.
8. Build and run the relevant regression tests on Android.

For schema changes that Room can safely infer, prefer Room `AutoMigration`.

For ambiguous or data-transforming changes such as complex renames, deletes, or custom data conversion,
use an explicit Room migration/`AutoMigrationSpec` as appropriate.

Do not create a migration merely because a feature was added if that feature does not change the database
schema.

### Destructive migration is prohibited

Do not add:

```kotlin
fallbackToDestructiveMigration()
```

or equivalent destructive migration behavior as a normal recovery mechanism.

If Rovia encounters a newer database schema without a valid migration path, the development/build/test
process must expose the missing migration rather than silently deleting the user's database.

User-owned persistent data must never be intentionally discarded during normal schema upgrades.

### Migration testing contract

Rovia uses Room's migration testing support inside `:data:database`.

Current test:

```text
data/database/src/androidTest/kotlin/
└── com/rovia/music/data/database/
    └── RoviaDatabaseMigrationTest.kt
```

The current baseline test verifies that database version 1 can be created with:

```text
recent_plays
excluded_folders
```

Future migration tests must verify both:

```text
schema correctness
+
data preservation
```

The intended future test flow is:

```text
create Version N database
        ↓
insert representative existing user data
        ↓
run N → N+1 migration
        ↓
validate new schema
        ↓
validate old data is still present
```

The database instrumentation tests are run with:

```powershell
.\gradlew.bat :data:database:connectedDebugAndroidTest
```

Do not delete the user's real `rovia.db` merely to make a migration test pass. Migration tests use their own
test database.

### Database source-of-truth rule

The following distinction is mandatory:

```text
MediaStore
    = source of truth for local audio/media

Room / SQLite
    = source of truth for Rovia-owned persistent application state
```

Never copy the entire MediaStore library into Room merely because a new feature needs persistence.

### Database growth policy

Future persistent features may reuse the existing `:data:database` module.

Examples include:

```text
Favorites
Playlists
Play History
Play Counts
Pinned Albums
Pinned Artists
```

Before adding another persistence technology, determine whether the existing Rovia Room database can
represent the required state.

Keep database implementation details isolated inside `:data:database` and expose domain-level repository
interfaces through the appropriate `core:*` API modules.

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
"something that looks better" without updating the README and testing the affected screens.

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
7. Document any new geometry or shape contract in this README.
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

The active main Navigation 3 destinations are:

```text
Home
Search
Library
Settings
FolderFilter
About
```

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

Child navigation from the active top-level page:
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
4. Keep forward navigation instantaneous.
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

The architecture is a layered root `Box`:

```text
Root Box
│
├── Navigation content
│
└── UnifiedPlayerSheet
    ├── Full Player layer
    ├── MiniPlayer layer
    └── NavigationBar layer
```

The MiniPlayer and NavigationBar are deliberately separate layers.

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

`MiniPlayer` receives callbacks such as:

```text
onPrevious
onPlayPause
onNext
onOpenPlayer
```

Do not add a second back callback to MiniPlayer.

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

This prevents MiniPlayer content/background from remaining over the navigation area.

Do not merge both into one parent card.

---

# 28. PLAYER SCREEN OWNERSHIP

`feature/player` contains:

```text
PlayerRoute
PlayerScreen
PlayerViewModel
PlayerUiState
LyricViewer
```

`PlayerRoute` connects application APIs to the UI.

`PlayerScreen` renders Player state.

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
)
```

Important semantics:

- `title` is non-null;
- `artist` may be null;
- `album` may be null;
- technical metadata may be null;
- artwork may be null.

Do not turn `Track.title` into nullable merely because MediaStore metadata can be incomplete.

The repository keeps a non-null title fallback.

---

# 35. MEDIASTORE QUERY

`MediaStoreMusicRepository` queries the external audio collection.

Current projection includes:

```text
_ID
TITLE
ARTIST
ALBUM
DURATION
DATE_ADDED
DISPLAY_NAME
MIME_TYPE
SAMPLERATE
BITRATE
```

The repository builds real `Track` objects from these values.

The repository does not use the database as a mirror of MediaStore. Application-owned persistence is handled separately by `:data:database`.

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

Home requests:

```text
limit = 10
```

sorted by:

```text
DATE_ADDED DESC
```

This is actual MediaStore information.

Do not fabricate recently added tracks.

Do not load the entire library just to render this section.

---

# 38. LIBRARY

Library has two explicit content modes:

```text
All Songs
Folders
```

The selector uses Material 3 Expressive `ButtonGroup` APIs rather than a custom segmented control.

All Songs mode requests the complete local audio collection through `MusicRepository.getAllTracks()`.

Current all-song sort:

```text
TITLE COLLATE NOCASE ASC
```

Folders mode uses `FolderBrowserRepository` and renders the MediaStore-derived folder hierarchy.

The Library does not rescan the filesystem when switching between these modes.

The Library renders real tracks through `TrackRow`.

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

# 39. RECENT PLAY

Recent Play is persistent across application restarts.

Current implementation stores at most:

```text
10 tracks
```

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

1. Remove/replace the existing entry with the same track ID.
2. Update `last_played_at_epoch_millis`.
3. Trim the table so only the latest 10 entries remain.
4. Expose the database-backed state through `StateFlow`.

Recent Play persistence stores application-owned playback history only. It does not modify or replace
MediaStore data.

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
Recent Play
Recently Added
```

Recent Play uses a horizontal two-row artwork grid when enough entries exist.

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
lyrics visibility -> false
lyrics state -> reset
```

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

When the current track ID changes, the ViewModel cancels the previous lyric load and starts loading lyrics for the new track.

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

Settings currently has no persistent settings state beyond explicitly implemented application data such as Folder Filter selections and Recent Play.

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

`HomeViewModel` currently combines:

```text
MusicRepository.getRecentlyAdded(limit = 10)
PlaybackController.recentPlays
```

and exposes a `HomeUiState` through `StateFlow`.

Errors are represented as an actual error state.

---

# 75. LIBRARY VIEWMODEL

`LibraryViewModel` requests:

```text
MusicRepository.getAllTracks()
```

and exposes a `LibraryUiState` through `StateFlow`.

No persistence layer is involved.

---

# 76. SEARCH VIEWMODEL

`SearchRoute` observes text changes from Compose `TextFieldState` and forwards the query to `SearchViewModel`.

Search processing is local to the application and the MediaStore-backed track collection.

No network search is involved.

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
- Recent Play is limited to 10.
- Search limits results to 50.
- Album artwork uses thumbnail loading.
- There is only one playback service/player path.
- The app does not continuously scan the entire filesystem.
- No network image loading exists.
- No background worker is introduced without a real requirement.
- The application database stores only bounded application-owned state and must not mirror the complete MediaStore library.

Do not optimize by introducing new caching layers before they are needed.

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
Extended listening history beyond the bounded Recent Play feature
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

Rovia has an application-owned Room 3 database backed by Android SQLite.

Current persistent data:

```text
Recent Play
Folder Filter selections
```

Current database module:

```text
:data:database
```

Current schema version:

```text
1
```

The database is not a mirror of the device's MediaStore library.

The database does not currently persist:

```text
Favorites
Most Played
Playlists
queue state
open folder/navigation path
language selection
```

Language follows Android resources.

Settings remains a UI shell except for explicitly implemented persistence such as Folder Filter.

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

When asked to change Rovia, an AI coding agent must follow this workflow exactly.

## Step 1
Read this `Agents.md` before modifying Rovia.

## Step 2
Inspect the actual target file and surrounding implementation.

## Step 3
Identify the owning module and preserve the documented architecture.

## Step 4
Create a checkpoint before risky interaction, navigation, playback, database, or migration changes.

## Step 5
Make the smallest change that implements the requested behavior.

## Step 6
Do not touch unrelated architecture, files, modules, UI behavior, or dependencies.

## Step 7
Compile the application with the mandatory full Debug build:

```powershell
.\gradlew.bat :app:assembleDebug
```

## Step 8
Only when `:app:assembleDebug` succeeds, install the resulting Debug APK:

```powershell
.\gradlew.bat :app:installDebug
```

## Step 9
Run the relevant runtime/regression test on the Android device or emulator.

## Step 10
If the change is verified successfully, create a structured Git commit immediately.

## Step 11
Remind the developer that the verified change must be committed if they have not committed it yet.

## Step 12
Do not leave a successfully verified change intentionally uncommitted unless the developer explicitly requests a different checkpoint strategy.

A failed build or failed runtime verification is a checkpoint, not a reason to create a misleading "successful" commit.

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
data/database/src/main/kotlin/com/rovia/music/data/database/repository/RoomRecentPlayRepository.kt
data/database/src/main/kotlin/com/rovia/music/data/database/repository/RoomFolderFilterRepository.kt
data/media-store/src/main/kotlin/com/rovia/music/data/media/store/MediaStoreMusicRepository.kt
data/media-store/src/main/kotlin/com/rovia/music/data/media/store/EmbeddedLyricsRepository.kt
feature/player/src/main/kotlin/com/rovia/music/feature/player/PlayerScreen.kt
feature/player/src/main/kotlin/com/rovia/music/feature/player/PlayerViewModel.kt
feature/player/src/main/kotlin/com/rovia/music/feature/player/LyricViewer.kt
```

Do not make a broad regex-based replacement in these files without inspecting the exact source first.

---

# 88. POWER-SHELL CHANGE WORKFLOW

The project is developed on Windows.

The preferred source-change workflow is incremental and observable:

1. Inspect the actual target path before making changes.
2. For new files, create the required directory and file explicitly.
3. For Kotlin-only source migration, move `.kt` files only; do not modify package declarations.
4. Verify the destination tree before deleting an old directory.
5. Compile immediately after each meaningful structural change.
6. Only remove an empty old source directory after the moved files compile successfully.
7. Prefer complete, deterministic file operations over broad regex replacements when the target
   source is structured and known.
8. Never use a script that can close the developer's PowerShell session unexpectedly.
9. Do not use `exit` in a script intended to be pasted into an interactive PowerShell session.
10. Keep the terminal available so the real compiler error can be inspected.

A failed compile is a checkpoint, not a reason to perform unrelated cleanup.

---

# 89. BUILD VERIFICATION

The mandatory application-wide Debug verification command after every source change is:

```powershell
.\gradlew.bat :app:assembleDebug
```

This command must be run after the requested change has been implemented, before the change is considered complete.

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
Git commit
```

Do not skip the install step when a successful application-wide Debug build was produced for a runtime-affecting change.

---

# 90.1. MANDATORY GIT COMMIT POLICY

Every successfully implemented and verified change must be committed.

A successful change is:

```text
requested change implemented
        ↓
:app:assembleDebug -> SUCCESS
        ↓
:app:installDebug -> SUCCESS
        ↓
relevant runtime/regression verification -> PASS
        ↓
structured Git commit
```

The AI coding agent must actively remind the developer to commit the change when the change has been verified
but no commit has been created yet.

## Commit messages are architectural labels

Commit messages must follow the ownership of the changed code/module.

The prefix identifies the primary project area that owns the change:

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

Use the prefix that matches the architecture, not a generic Git convention.

### `feature:`

Use for changes owned by a feature module such as:

```text
feature/home
feature/search
feature/library
feature/player
feature/settings
```

Examples:

```text
feature: Adding a maintainer profile to the About page in settings
feature: Fixing folder back navigation in the Library feature
feature: Improving synchronized lyric rendering in the Player feature
```

### `core:`

Use for changes owned by shared Core APIs, models, or shared UI components.

Example:

```text
core: Fixing a bug in the MiniPlayer within the core UI
```

Other valid examples:

```text
core: Updating playback state contracts in core playback API
core: Fixing TrackRow artwork handling in core UI
```

### `data:`

Use for changes owned by:

```text
:data:database
:data:media-store
```

Examples:

```text
data: Adding persistent Folder Filter migration support
data: Fixing embedded lyric parsing in MediaStore
```

### `playback:`

Use for changes owned by:

```text
:playback:media3
```

Examples:

```text
playback: Fixing queue state restoration in Media3 playback
playback: Updating MediaSession playback state handling
```

### `app:`

Use for application composition, root navigation, Activity, theme entry-point, or `UnifiedPlayerSheet`
changes owned by `:app`.

Examples:

```text
app: Fixing Player Back behavior in Navigation.kt
app: Updating UnifiedPlayerSheet predictive back handling
```

### `build:`

Use for Gradle, version catalog, signing configuration, build scripts, or build tooling changes.

Examples:

```text
build: Updating Android Gradle Plugin configuration
build: Fixing Debug installation workflow
```

### `test:`

Use when the primary change is isolated to tests or test infrastructure.

Examples:

```text
test: Adding Room migration coverage for excluded folders
test: Updating Library navigation regression tests
```

### `docs:`

Use for changes to engineering documentation such as this `Agents.md`.

Example:

```text
docs: Updating AI agent commit and verification rules
```

## Commit message quality rules

A commit message must:

```text
1. identify the owning architectural area;
2. describe the actual change;
3. mention the relevant feature/component when useful;
4. be specific enough to identify the reason for the commit;
5. remain readable without opening the diff.
```

Do not use vague commit messages such as:

```text
update
changes
fix
bug fix
misc
stuff
work
wip
test
final
update files
small changes
```

Do not use a misleading prefix merely because it is convenient.

For example:

```text
core: Fixing a bug in the MiniPlayer within the core UI
```

is correct when the changed MiniPlayer belongs to `:core:ui`.

A Navigation 3 change in `app/` should not be committed as:

```text
feature: ...
```

unless the actual owning code is a feature module.

## Scope discipline

Prefer one logical change per commit.

Do not mix unrelated changes into one commit merely because they were made during the same session.

For example:

```text
feature: Fixing Library folder back navigation
```

should not also contain unrelated:

```text
data: database schema changes
core: MiniPlayer redesign
```

unless the requested change genuinely requires all of those layers.

When one requested change necessarily crosses multiple modules, use the prefix of the primary owning layer and make the
message describe the complete behavior.

## Mandatory commit workflow

After successful verification:

```powershell
git status

git add .

git diff --cached --check

git diff --cached --name-only

git commit -m "PRIMARY_AREA: Specific description of the verified change"
```

Then verify:

```powershell
git log -1 --oneline
git status
```

The working tree should be clean for a fully completed change unless there are explicitly unrelated local modifications.

The AI agent must not silently finish a successful task and leave the developer unaware that a commit is still required.

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
[ ] seek tap works
[ ] seek drag works
[ ] previous/next work
[ ] lyrics button works
[ ] lyrics synchronization follows playback position
[ ] switching tracks resets lyrics visibility/state
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

Test tracks with:

```text
[ ] normal title
[ ] missing title
[ ] missing artist
[ ] missing album
[ ] missing artwork
[ ] different sample rates
[ ] different bitrates
[ ] different MIME types
[ ] embedded lyrics
[ ] no embedded lyrics
```

No metadata should be fabricated.

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

This README documents the intended/current architecture, but the code is the final runtime authority.

If code and README genuinely disagree because a new feature was intentionally implemented:

1. inspect the code;
2. verify the new behavior;
3. update this README together with the intentional architectural change.

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
Room 3 only where persistent application state is genuinely required
incremental Room migrations with preserved user data
MediaStore as the source of truth for the local media library
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
5. update this README when the intentional geometry changes.

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

# 108. FOLDER FILTER HEADER CONTRACT

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

# 109. KOTLIN-ONLY SOURCE TREE CONTRACT

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

# 110. SOURCE TREE MIGRATION RECORD

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

# 111. FINAL SOURCE-TREE AUDIT CONTRACT

Before declaring a source-tree migration complete, verify:

```powershell
Get-ChildItem -Path ".pp", ".\core", ".\data", ".\playback", ".eature" `
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
Get-ChildItem -Path ".pp", ".\core", ".\data", ".\playback", ".eature" `
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
Get-ChildItem -Path ".pp", ".\core", ".\data", ".\playback", ".eature" `
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

# 112. UI ALIGNMENT REGRESSION CHECKLIST

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

# 113. CHANGE LOG: CURRENT UI AND ARCHITECTURE REFINEMENT

The current README update records the following intentional changes made during the latest
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

## Database integration

- Added the dedicated `:data:database` module.
- Added Room 3 with KSP and the Android framework SQLite driver.
- Added persistent `recent_plays` storage with a maximum of 10 records.
- Added persistent `excluded_folders` storage for Folder Filter selections.
- Added Core repository abstractions for Recent Play and folder scanning.
- Removed the obsolete `SessionRecentPlayStore`.
- Kept MediaStore as the source of truth for local audio data.
- Added Room schema export configuration.
- Established database schema version `1`.
- Added and version-controlled the exported schema baseline:
  `data/database/schemas/com.rovia.music.data.database.RoviaDatabase/1.json`.
- Added Room migration-testing support.
- Added `RoviaDatabaseMigrationTest` as the version-1 migration baseline test.
- Verified the migration test passes on the Android 16 emulator.
- Verified Recent Play survives force-stop and application restart on the Android 16 test device.
- Verified Folder Filter selections survive force-stop and application restart on the Android 16 test device.
- Verified the SQLite schema contains `recent_plays` and `excluded_folders`.
- Verified Debug build succeeds.
- Verified Release build succeeds with R8/resource shrinking enabled.
- Migration policy explicitly prohibits destructive database resets for normal schema evolution.

## Database migration contract

The current database is version `1`. Future schema changes must evolve incrementally:

```text
v1 → v2 → v3 → ...
```

Existing exported schema versions remain unchanged and are never replaced by newer schema files.

Each migration must be implemented and tested before the corresponding application version is considered
complete.

At minimum, migration tests must prove:

```text
old schema opens
        ↓
migration executes
        ↓
new schema is valid
        ↓
existing user data remains intact
```

Adding a feature without changing the database schema does not require a migration.

Adding Favorites, Playlists, Play History, or another future persistent feature will require a new schema
version only when its database schema actually changes.

## Navigation contract verification

The current navigation contract is:

```text
Home / Search / Library
    = top-level siblings

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

Future navigation work must keep this contract and update the Navigation 3 sections of this README whenever
the implementation intentionally changes.

## Verification

The database and architecture were verified through:

```text
:data:database:compileDebugKotlin
:data:database:connectedDebugAndroidTest
:app:assembleDebug
:app:assembleRelease
```

The Android 16 runtime verification also confirmed:

```text
Recent Play persistence       -> PASS
Folder Filter persistence     -> PASS
Room database creation        -> PASS
SQLite schema                 -> PASS
Room v1 migration baseline    -> PASS
```

The database migration test uses a dedicated test database and does not modify the real user database.

The existing source-tree migration rules remain unchanged. This database integration is an intentional
architecture change required to support persistent application-owned state while preserving MediaStore as
the local-media source of truth.
