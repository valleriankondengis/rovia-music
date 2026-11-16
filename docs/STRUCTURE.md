# Rovia Repository Structure

**Documentation root:** `docs/`

The file and directory layout of the repository, followed by the documentation package layout. Generated build outputs and local machine files are not part of the map.

## 1. Repository tree

Generated build outputs, local machine files and raw audit captures are not included.

```text
rovia/
├── app/
│   ├── schemas/
│   │   └── com.rovia.music.data.local.database.RoviaDatabase/
│   │       └── 1.json
│   ├── src/
│   │   └── main/
│   │       ├── kotlin/
│   │       │   └── com/
│   │       │       └── rovia/
│   │       │           └── music/
│   │       │               ├── data/
│   │       │               │   ├── local/
│   │       │               │   │   ├── dao/
│   │       │               │   │   │   ├── AlbumDao.kt
│   │       │               │   │   │   ├── ArtistDao.kt
│   │       │               │   │   │   ├── ArtworkAssetDao.kt
│   │       │               │   │   │   ├── CatalogRelationDao.kt
│   │       │               │   │   │   ├── ExcludedFolderDao.kt
│   │       │               │   │   │   ├── GenreDao.kt
│   │       │               │   │   │   ├── LikedTrackDao.kt
│   │       │               │   │   │   ├── ListeningSegmentDao.kt
│   │       │               │   │   │   ├── MediaStoreSyncStateDao.kt
│   │       │               │   │   │   ├── PlaybackActivityDao.kt
│   │       │               │   │   │   ├── PlaybackSessionDao.kt
│   │       │               │   │   │   ├── PlaybackTrackEventDao.kt
│   │       │               │   │   │   ├── PlaylistDao.kt
│   │       │               │   │   │   ├── TrackDao.kt
│   │       │               │   │   │   └── UserActionEventDao.kt
│   │       │               │   │   ├── database/
│   │       │               │   │   │   ├── RoviaDatabase.kt
│   │       │               │   │   │   └── RoviaDatabaseFactory.kt
│   │       │               │   │   └── entity/
│   │       │               │   │       ├── AlbumArtistCrossRef.kt
│   │       │               │   │       ├── AlbumEntity.kt
│   │       │               │   │       ├── ArtistEntity.kt
│   │       │               │   │       ├── ArtworkAssetEntity.kt
│   │       │               │   │       ├── ExcludedFolderEntity.kt
│   │       │               │   │       ├── GenreEntity.kt
│   │       │               │   │       ├── LikedTrackEntity.kt
│   │       │               │   │       ├── ListeningSegmentEntity.kt
│   │       │               │   │       ├── MediaStoreSyncStateEntity.kt
│   │       │               │   │       ├── PlaybackActivityEntity.kt
│   │       │               │   │       ├── PlaybackSessionEntity.kt
│   │       │               │   │       ├── PlaybackTrackEventEntity.kt
│   │       │               │   │       ├── PlaylistEntity.kt
│   │       │               │   │       ├── PlaylistTrackEntity.kt
│   │       │               │   │       ├── TrackArtistCrossRef.kt
│   │       │               │   │       ├── TrackEntity.kt
│   │       │               │   │       ├── TrackGenreCrossRef.kt
│   │       │               │   │       └── UserActionEventEntity.kt
│   │       │               │   ├── lyrics/
│   │       │               │   │   ├── EmbeddedLyricsReader.kt
│   │       │               │   │   └── TimedLyricWords.kt
│   │       │               │   ├── mediastore/
│   │       │               │   │   ├── AlbumParser.kt
│   │       │               │   │   ├── ArtistCreditParser.kt
│   │       │               │   │   ├── CatalogArtworkSelector.kt
│   │       │               │   │   ├── LocalAudioTrack.kt
│   │       │               │   │   ├── MediaStoreAudioRepository.kt
│   │       │               │   │   ├── MediaStoreCatalogSynchronizer.kt
│   │       │               │   │   ├── MediaStoreFolderRepository.kt
│   │       │               │   │   └── MusicFolder.kt
│   │       │               │   └── repository/
│   │       │               │       ├── DatabaseSearchRepository.kt
│   │       │               │       ├── FolderFilterRepository.kt
│   │       │               │       ├── LibraryRepository.kt
│   │       │               │       ├── PlaybackHistoryRepository.kt
│   │       │               │       ├── RoomLibraryRepository.kt
│   │       │               │       └── SharedPreferencesFolderFilterRepository.kt
│   │       │               ├── feature/
│   │       │               │   ├── album/
│   │       │               │   │   └── AlbumPage.kt
│   │       │               │   ├── artist/
│   │       │               │   │   └── ArtistPage.kt
│   │       │               │   ├── genre/
│   │       │               │   │   └── GenrePage.kt
│   │       │               │   ├── history/
│   │       │               │   │   └── HistoryScreen.kt
│   │       │               │   ├── home/
│   │       │               │   │   ├── HomeRecentlyAdded.kt
│   │       │               │   │   └── HomeScreen.kt
│   │       │               │   ├── library/
│   │       │               │   │   ├── LibraryScreen.kt
│   │       │               │   │   └── LibraryViewModel.kt
│   │       │               │   ├── player/
│   │       │               │   │   ├── AudioOutput.kt
│   │       │               │   │   ├── PlayerLyric.kt
│   │       │               │   │   ├── PlayerScreen.kt
│   │       │               │   │   └── QueueSheet.kt
│   │       │               │   ├── search/
│   │       │               │   │   ├── SearchEngine.kt
│   │       │               │   │   └── SearchScreen.kt
│   │       │               │   ├── settings/
│   │       │               │   │   ├── AboutScreen.kt
│   │       │               │   │   ├── AudioScreen.kt
│   │       │               │   │   ├── FolderFilterScreen.kt
│   │       │               │   │   └── SettingsScreen.kt
│   │       │               │   └── single/
│   │       │               │       └── SinglePage.kt
│   │       │               ├── playback/
│   │       │               │   ├── PlaybackDecoderStore.kt
│   │       │               │   ├── bitperfect/
│   │       │               │   │   ├── AudioOutputMode.kt
│   │       │               │   │   ├── UsbDetachModeReverter.kt
│   │       │               │   │   ├── a14/
│   │       │               │   │   │   ├── NativeA14.kt
│   │       │               │   │   │   ├── NativeA14MediaClock.kt
│   │       │               │   │   │   ├── NativeA14PlaybackCoordinator.kt
│   │       │               │   │   │   └── NativeA14Renderer.kt
│   │       │               │   │   ├── dap/
│   │       │               │   │   │   ├── DapMediaClock.kt
│   │       │               │   │   │   ├── DapPlaybackCoordinator.kt
│   │       │               │   │   │   └── DapRenderer.kt
│   │       │               │   │   └── direct/
│   │       │               │   │       ├── DirectUsbController.kt
│   │       │               │   │       ├── DirectUsbControllerProvider.kt
│   │       │               │   │       ├── DirectUsbMediaClock.kt
│   │       │               │   │       ├── DirectUsbPlaybackCoordinator.kt
│   │       │               │   │       └── DirectUsbRenderer.kt
│   │       │               │   ├── controller/
│   │       │               │   │   └── PlaybackController.kt
│   │       │               │   ├── queue/
│   │       │               │   │   ├── PlaybackMetadataKeys.kt
│   │       │               │   │   └── PlaybackOrigin.kt
│   │       │               │   ├── service/
│   │       │               │   │   ├── PlaybackService.kt
│   │       │               │   │   └── RoviaRenderersFactory.kt
│   │       │               │   └── statistics/
│   │       │               │       └── PlaybackStatisticsRepository.kt
│   │       │               ├── ui/
│   │       │               │   ├── components/
│   │       │               │   │   ├── Artwork.kt
│   │       │               │   │   ├── ArtworkBackdrop.kt
│   │       │               │   │   ├── ArtworkBitmapLoader.kt
│   │       │               │   │   ├── ArtworkDiskCache.kt
│   │       │               │   │   ├── FadeScrim.kt
│   │       │               │   │   ├── Lyric.kt
│   │       │               │   │   ├── MiniPlayer.kt
│   │       │               │   │   ├── AudioPipeline.kt
│   │       │               │   │   ├── TrackInfo.kt
│   │       │               │   │   ├── TrackList.kt
│   │       │               │   │   └── TrackMenu.kt
│   │       │               │   ├── permissions/
│   │       │               │   │   └── AudioPermission.kt
│   │       │               │   └── theme/
│   │       │               │       └── Theme.kt
│   │       │               ├── util/
│   │       │               │   └── KotlinDateTime.kt
│   │       │               ├── uniffi/
│   │       │               │   └── rovia_uniffi.kt
│   │       │               ├── AppContainer.kt
│   │       │               ├── MainActivity.kt
│   │       │               └── Navigation.kt
│   │       ├── res/
│   │       │   ├── drawable/
│   │       │   │   ├── ic_add.xml
│   │       │   │   ├── ic_arrow_back.xml
│   │       │   │   ├── ic_arrow_forward.xml
│   │       │   │   ├── ic_bluetooth.xml
│   │       │   │   ├── ic_folder.xml
│   │       │   │   ├── ic_history.xml
│   │       │   │   ├── ic_home.xml
│   │       │   │   ├── ic_launcher_background.xml
│   │       │   │   ├── ic_launcher_foreground.xml
│   │       │   │   ├── ic_library.xml
│   │       │   │   ├── ic_lyrics.xml
│   │       │   │   ├── ic_more_vert.xml
│   │       │   │   ├── ic_pause.xml
│   │       │   │   ├── ic_play.xml
│   │       │   │   ├── ic_play_arrow.xml
│   │       │   │   ├── ic_queue.xml
│   │       │   │   ├── ic_repeat.xml
│   │       │   │   ├── ic_repeat_one.xml
│   │       │   │   ├── ic_search.xml
│   │       │   │   ├── ic_settings.xml
│   │       │   │   ├── ic_shuffle.xml
│   │       │   │   ├── ic_skip_next.xml
│   │       │   │   ├── ic_skip_previous.xml
│   │       │   │   ├── ic_smartphone.xml
│   │       │   │   ├── ic_sort.xml
│   │       │   │   └── ic_usb.xml
│   │       │   ├── mipmap-anydpi-v26/
│   │       │   │   ├── ic_launcher.xml
│   │       │   │   └── ic_launcher_round.xml
│   │       │   ├── mipmap-hdpi/
│   │       │   │   ├── ic_launcher.webp
│   │       │   │   └── ic_launcher_round.webp
│   │       │   ├── mipmap-mdpi/
│   │       │   │   ├── ic_launcher.webp
│   │       │   │   └── ic_launcher_round.webp
│   │       │   ├── mipmap-xhdpi/
│   │       │   │   ├── ic_launcher.webp
│   │       │   │   └── ic_launcher_round.webp
│   │       │   ├── mipmap-xxhdpi/
│   │       │   │   ├── ic_launcher.webp
│   │       │   │   └── ic_launcher_round.webp
│   │       │   ├── mipmap-xxxhdpi/
│   │       │   │   ├── ic_launcher.webp
│   │       │   │   └── ic_launcher_round.webp
│   │       │   ├── values/
│   │       │   │   ├── strings.xml
│   │       │   │   └── themes.xml
│   │       │   ├── values-in/
│   │       │   │   └── strings.xml
│   │       │   ├── values-night/
│   │       │   │   └── themes.xml
│   │       │   ├── xml/
│   │       │   │   ├── backup_rules.xml
│   │       │   │   └── data_extraction_rules.xml
│   │       │   └── resources.properties
│   │       └── AndroidManifest.xml
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── docs/
│   ├── direct-usb/
│   │   ├── ANDROID_USB_UNIFFI.md
│   │   ├── DIRECT_USB_PIPELINE.md
│   │   ├── HARDWARE_VOLUME.md
│   │   ├── LIFECYCLE_AND_POSITION.md
│   │   ├── QUEUE_AND_PACKETIZATION.md
│   │   ├── README.md
│   │   ├── RUST_TRANSPORT.md
│   │   ├── SYMPHONIA_PLAYBACK_ENGINE.md
│   │   └── UAC_DESCRIPTOR_AND_CLOCK.md
│   ├── hardware/
│   │   ├── ANDROID_DEVICE.md
│   │   ├── CAYIN_RU3.md
│   │   ├── README.md
│   │   └── VALIDATED_COMBINATION.md
│   ├── media3/
│   │   ├── DECODER_POLICY.md
│   │   ├── DIRECT_USB_PLAYBACK_COORDINATION.md
│   │   ├── EXOPLAYER_PIPELINE.md
│   │   ├── MEDIA3_UPGRADE_POLICY.md
│   │   └── README.md
│   ├── modes/
│   │   ├── DAP_MODE.md
│   │   ├── DIRECT_USB.md
│   │   ├── MODE_ORCHESTRATION.md
│   │   ├── NATIVE_ANDROID_14.md
│   │   └── README.md
│   ├── pcm/
│   │   ├── BIT_PERFECT_CONTRACT.md
│   │   ├── EXACT_PCM_ADAPTER.md
│   │   ├── PCM_FORMAT_MODEL.md
│   │   └── README.md
│   ├── verification/
│   │   ├── DIAGNOSTICS.md
│   │   ├── README.md
│   │   ├── Result_Android_AudioFlinger.md
│   │   ├── result_c2_android_flac_decoder.md
│   │   └── VALIDATION_MATRIX.md
│   ├── ARCHITECTURE.md
│   ├── README.md
│   ├── REFERENCE.md
│   └── STRUCTURE.md
├── gradle/
│   ├── wrapper/
│   │   ├── gradle-wrapper.jar
│   │   └── gradle-wrapper.properties
│   └── libs.versions.toml
├── rust/
│   ├── decoder/
│   │   ├── src/
│   │   │   ├── engine.rs
│   │   │   ├── error.rs
│   │   │   ├── format.rs
│   │   │   ├── info.rs
│   │   │   └── lib.rs
│   │   ├── tests/
│   │   │   ├── fixtures/
│   │   │   └── decode.rs
│   │   └── Cargo.toml
│   ├── direct-usb/
│   │   ├── src/
│   │   │   ├── adapter.rs
│   │   │   ├── clock.rs
│   │   │   ├── dap.rs
│   │   │   ├── dap_session.rs
│   │   │   ├── descriptors.rs
│   │   │   ├── flush_barrier.rs
│   │   │   ├── lib.rs
│   │   │   ├── native.rs
│   │   │   ├── pcm.rs
│   │   │   ├── pcm_source.rs
│   │   │   └── playback.rs
│   │   ├── Cargo.toml
│   │   └── uniffi.toml
│   ├── uniffi/
│   │   ├── src/
│   │   │   └── main.rs
│   │   └── Cargo.toml
│   ├── Cargo.lock
│   └── Cargo.toml
├── scripts/
│   └── build-rust.sh
├── .gitignore
├── build.gradle.kts
├── Export-ProjectSource.ps1
├── gradle.properties
├── gradlew
├── gradlew.bat
├── README.md
└── settings.gradle.kts
```

## 2. Documentation tree

The documentation package:

```text
docs/
├── ARCHITECTURE.md
├── README.md
├── REFERENCE.md
├── STRUCTURE.md
├── direct-usb
│   ├── ANDROID_USB_UNIFFI.md
│   ├── LIFECYCLE_AND_POSITION.md
│   ├── QUEUE_AND_PACKETIZATION.md
│   ├── README.md
│   ├── RUST_TRANSPORT.md
│   ├── SYMPHONIA_PLAYBACK_ENGINE.md
│   └── UAC_DESCRIPTOR_AND_CLOCK.md
├── hardware
│   ├── ANDROID_DEVICE.md
│   ├── CAYIN_RU3.md
│   ├── README.md
│   └── VALIDATED_COMBINATION.md
├── media3
│   ├── DECODER_POLICY.md
│   ├── DIRECT_USB_PLAYBACK_COORDINATION.md
│   ├── EXOPLAYER_PIPELINE.md
│   ├── MEDIA3_UPGRADE_POLICY.md
│   └── README.md
├── modes
│   ├── DAP_MODE.md
│   ├── DIRECT_USB.md
│   ├── MODE_ORCHESTRATION.md
│   ├── NATIVE_ANDROID_14.md
│   └── README.md
├── pcm
│   ├── BIT_PERFECT_CONTRACT.md
│   ├── EXACT_PCM_ADAPTER.md
│   ├── PCM_FORMAT_MODEL.md
│   └── README.md
└── verification
    ├── DIAGNOSTICS.md
    ├── README.md
    ├── Result_Android_AudioFlinger.md
    ├── VALIDATION_MATRIX.md
    └── result_c2_android_flac_decoder.md
```

## 3. Main application package responsibilities

| Area | Responsibility |
|---|---|
| `app/src/main/kotlin/com/rovia/music/` | Kotlin application entry points, dependency container and navigation. |
| `data/local/dao/` | Room DAO interfaces for catalog, playback, history and user data. |
| `data/local/database/` | Room database definition and factory. |
| `data/local/entity/` | Persisted entities, cross-reference tables and relation models. |
| `data/lyrics/` | Embedded lyrics parsing and timed-word representation. |
| `data/mediastore/` | MediaStore access, metadata parsing, catalog synchronization, artwork selection and folder representation. |
| `data/repository/` | Repository implementations for library, search, folder filters and playback history. |
| `feature/` | Compose screens and feature-specific presentation logic. |
| `playback/controller/` | Playback command and control boundary. |
| `playback/queue/` | Playback metadata keys and playback-origin metadata. |
| `playback/service/` | ExoPlayer and MediaSession service lifecycle. |
| `playback/statistics/` | Playback statistics repository. |
| `playback/bitperfect/direct/` | Android USB permission, connection and Rust session integration. |
| `uniffi/` | Generated UniFFI Kotlin output; do not edit manually. |
| `ui/components/` | Reusable Compose components. |
| `ui/permissions/` | Runtime permission helpers. |
| `ui/theme/` | Application theme definitions. |
| `util/` | Shared utility functions. |
| `app/src/main/res/` | Android drawables, mipmaps, strings, themes and XML resources. |

## 4. Rust crate responsibilities

| Path | Responsibility |
|---|---|
| `rust/decoder/src/engine.rs` | Symphonia demux/decode engine: seekable source, one packet per decode step, accurate seek, EOS handling. Independent of the USB backend. |
| `rust/decoder/src/format.rs` | Decoded sample-format model shared by the decoder and the PCM adapter. |
| `rust/decoder/src/info.rs` | Source information and decoded-block contracts. |
| `rust/decoder/src/error.rs` | Typed decoder errors (unsupported, I/O, decode). |
| `rust/direct-usb/src/adapter.rs` | Exact integer/float PCM adaptation from decoded blocks to the configured device format. |
| `rust/direct-usb/src/playback.rs` | Playback producer engine: prebuffer, backpressure, pause/seek re-anchoring, completed-frame position, EOS drain. |
| `rust/direct-usb/src/lib.rs` | Exported Rust API and Direct USB session façade, including the typed UniFFI source-playback API. |
| `rust/direct-usb/src/descriptors.rs` | USB/UAC descriptor parsing and topology resolution. |
| `rust/direct-usb/src/clock.rs` | UAC clock configuration and packet-clock support. |
| `rust/direct-usb/src/pcm.rs` | Bounded PCM byte queue and frame-aligned writes. |
| `rust/direct-usb/src/flush_barrier.rs` | Flush/write synchronization barrier. |
| `rust/direct-usb/src/native.rs` | Android/libusb session, worker, asynchronous transfers, feedback, lifecycle and counters. |
| `rust/uniffi/` | UniFFI scaffolding crate. |

## 5. Important integration files

| File | Current role |
|---|---|
| `app/src/main/kotlin/com/rovia/music/playback/service/PlaybackService.kt` | ExoPlayer and MediaSession service lifecycle; owns the output-mode state, rebuilds the player transactionally on mode changes and feeds the current media item to all three Bit-Perfect coordinators. |
| `app/src/main/kotlin/com/rovia/music/playback/service/RoviaRenderersFactory.kt` | Builds the standard Android audio renderers for OFF, or the active mode's control renderer alone when a Bit-Perfect mode is selected. |
| `app/src/main/kotlin/com/rovia/music/playback/controller/PlaybackController.kt` | Existing playback controller. |
| `app/src/main/kotlin/com/rovia/music/playback/bitperfect/AudioOutputMode.kt` | The four output modes and their persisted, observable store shared by Settings and the playback service. |
| `app/src/main/kotlin/com/rovia/music/playback/bitperfect/direct/DirectUsbController.kt` | Android USB discovery, permission, connection and Rust session coordination, including the source-playback bridge used by the coordinator. |
| `app/src/main/kotlin/com/rovia/music/playback/bitperfect/direct/DirectUsbPlaybackCoordinator.kt` | Serializes native playback commands, resolves the current item to a duplicated source descriptor, polls native status and anchors the playback clock to completed output frames. |
| `app/src/main/kotlin/com/rovia/music/playback/bitperfect/direct/DirectUsbRenderer.kt` | Media3 control renderer for Direct USB: no PCM and no AudioSink; coordinates the native engine and reports readiness, end of stream and errors. |
| `app/src/main/kotlin/com/rovia/music/playback/bitperfect/direct/DirectUsbMediaClock.kt` | The single Media3 media clock for Direct USB, backed by the native completed-frame position. |
| `app/src/main/kotlin/com/rovia/music/playback/bitperfect/a14/NativeA14.kt` | Native A14 support check (Android 14+, USB device, BIT_PERFECT mixer attributes), the exact-match mixer-attribute selector/verifier used by the mode's own AudioTrack, and the live status flow shown in Settings. |
| `app/src/main/kotlin/com/rovia/music/playback/bitperfect/a14/NativeA14PlaybackCoordinator.kt` | Serializes Native A14 playback: opens the Rust pull source, selects the exact BIT_PERFECT attribute, owns the AudioTrack and its PCM pump thread, verifies the route, and anchors the clock to the track's playback head. |
| `app/src/main/kotlin/com/rovia/music/playback/bitperfect/a14/NativeA14Renderer.kt` | Media3 control renderer for Native A14: no PCM and no AudioSink; coordinates the native pull source and reports readiness, end of stream and errors. |
| `app/src/main/kotlin/com/rovia/music/playback/bitperfect/a14/NativeA14MediaClock.kt` | The single Media3 media clock for Native A14, backed by the AudioTrack playback-head position. |
| `app/src/main/kotlin/com/rovia/music/playback/bitperfect/dap/DapPlaybackCoordinator.kt` | Serializes DAP playback commands against the Rust `DapSession`, resolves the current item to a duplicated source descriptor, polls native status and anchors the playback clock to AAudio frames read. |
| `app/src/main/kotlin/com/rovia/music/playback/bitperfect/dap/DapRenderer.kt` | Media3 control renderer for DAP: no PCM and no AudioSink; coordinates the Rust session and reports readiness, end of stream and errors. |
| `app/src/main/kotlin/com/rovia/music/playback/bitperfect/dap/DapMediaClock.kt` | The single Media3 media clock for DAP, backed by the native completed-frame position. |
| `app/src/main/kotlin/com/rovia/music/playback/bitperfect/UsbDetachModeReverter.kt` | USB detach broadcast handler that reverts Direct USB / Native A14 selections to OFF and disconnects the Direct session; registered by the playback service and the Settings audio screen. |
| `app/src/main/kotlin/com/rovia/music/playback/PlaybackDecoderStore.kt` | Process-wide holder for the platform decoder name reported by the player (standard path), shown in the pipeline sheet. |
| `rust/direct-usb/src/dap.rs` | Crate-internal AAudio FFI layer: exclusive-first stream open with negotiated-format verification, timed writes and frame counters. |
| `rust/direct-usb/src/dap_session.rs` | DAP session: the shared playback engine (Symphonia decode, exact adapter, bounded producer) over the AAudio sink, exposed to Kotlin as the UniFFI `DapSession` object. |
| `rust/direct-usb/src/pcm_source.rs` | Native A14 pull source: Symphonia decode plus the exact adapter at the track width, exposed to Kotlin as the UniFFI `NativePcmSource` object with block reads and seeks. |
| `app/src/main/kotlin/com/rovia/music/uniffi/rovia_uniffi.kt` | Generated UniFFI Kotlin wrapper for the Rust bridge library (`rovia_uniffi`). |
| `gradle/libs.versions.toml` | Central dependency and tool version catalog. |
| `scripts/build-rust.sh` | Rust host/Android build and binding generation workflow. |

## 6. Documentation map

- `README.md` is the navigation hub.
- `ARCHITECTURE.md` describes the playback architecture and the four output modes.
- Technical pages describe mechanisms, ownership and contracts.
- `REFERENCE.md` is the index of official technical sources.


