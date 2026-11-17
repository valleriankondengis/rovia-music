# Features That Require the Database

Every feature below reads or writes `rovia.db` through the repository named
in its access path; no screen touches a DAO directly.

| Feature | Tables | Access path |
| --- | --- | --- |
| Library catalog and browsing | `tracks`, `artists`, `albums`, `genres`, `track_artist_cross_ref`, `album_artist_cross_ref`, `track_genre_cross_ref` | `RoomLibraryRepository`, fed by `MediaStoreCatalogSynchronizer` |
| Search | `tracks`, `albums`, `artists`, `playlists`, `liked_tracks` plus catalog relations | `DatabaseSearchRepository` |
| Playlists | `playlists`, `playlist_tracks` | `UserLibraryRepository` |
| Liked songs | `liked_tracks` | `UserLibraryRepository` |
| Liked artists, albums, singles and EPs | `liked_entities` | `UserLibraryRepository` |
| Playback history | `playback_activities` | `PlaybackHistoryRepository` |
| Playback statistics | `playback_sessions`, `playback_track_events`, `listening_segments` | `PlaybackStatisticsRepository`, written by `PlaybackService` |
| MediaStore synchronization | `media_store_sync_state` plus all catalog tables | `MediaStoreCatalogSynchronizer` |

## Library catalog and browsing

The Library tabs (All Songs, Artists, Albums, Genres, Folders) and the
album, artist, single and genre pages render the catalog tables. The catalog
is a database mirror of MediaStore: the synchronizer keeps it current, and
the pages observe it through `RoomLibraryRepository` flows, so a rescan
updates every open screen without a reload.

## Search

`DatabaseSearchRepository` answers search from the database in both
directions the UI needs: a song query surfaces the track's liked row and the
playlists containing it, and a playlist-name query surfaces the playlist
itself. Liked results read the `liked_tracks` snapshots, so a liked song
whose file left the catalog can still appear as a result.

## Playlists

Creating, renaming and deleting playlists, adding and removing tracks, and
the playlist page itself all run on `playlists` and `playlist_tracks`.
Membership order is the stored `sortOrder`; the page's queue is assembled
from rows whose track still resolves in the catalog, while rows whose files
disappeared remain visible through their snapshots.

## Liked songs

Liking is a toggle: the track's row is inserted into or deleted from
`liked_tracks`. The same table drives the heart state shown by track menus
on every page, the Liked library tab, and the Liked page, which lists songs
most recently liked first.

## Liked artists, albums, singles and EPs

Entity likes live in `liked_entities`, independent of `liked_tracks`. The
Like button on the artist, album and single pages toggles a row keyed by
entity type and key; the Library's Liked tab lists the liked entities with
their snapshot artwork beside the liked songs entry, and each row navigates
back to its page.

## Playback history and statistics

`PlaybackService` records sessions, per-track events and listening segments
as playback happens. The History page reads per-track activity through
`PlaybackHistoryRepository`; aggregate statistics (for example most-played
views) read the session, event and segment tables through
`PlaybackStatisticsRepository`. The artist page's Top Songs list is one of
those aggregate views, scoped to a single artist: that artist's tracks ranked
by qualified play count, then total listening time, then most recent play,
resolved back to playable catalog tracks. The Wrapped recap reads the same
aggregates scoped to a selected period (today, this week, this month or
this year): top artists, tracks and albums, period totals and distinct
counts, assembled when the Wrapped page opens and presented as story slides.

## MediaStore synchronization

`media_store_sync_state` records where the last catalog synchronization
ended, so the synchronizer can reconcile changes instead of rebuilding the
catalog from scratch on every launch.

## Features that deliberately bypass the database

The External Player writes nothing and reads nothing here: a file opened
through Open With plays in its own isolated service with no catalog row,
no playback session, no track event and no history entry, so external
plays never appear in History, Top Songs or Wrapped. See
[the External Player document](../media3/EXTERNAL_PLAYER.md).

Recent searches are likewise kept out of the database on purpose: they
live in one JSON file in the app's cache directory
(`SearchHistoryStore`), so they vanish when the app cache is cleared and
can never grow the database.

## Schema tables without a runtime writer

Three tables are part of the exported schema but currently have no runtime
writer:

- `artwork_assets` — referenced by foreign keys from `tracks` and
  `playlist_tracks`; artwork itself is resolved from the tracks' content
  URIs at render time.
- `user_action_events` — reserved action records; no feature writes them.
- `excluded_folders` — folder filtering preferences are kept in
  `SharedPreferences` by `SharedPreferencesFolderFilterRepository`; the
  table and its DAO exist in the schema but are not used at runtime.
