# Rovia Feature Reference

Every user-facing feature of the app, described by what it does and how it
behaves. Playback internals live in the technical documents (see
[the documentation index](README.md)); the database side of the library
features is mapped in [the Room3 feature map](room3/features.md).

## Library and browsing

The Library screen browses the on-device catalog through seven tabs:
All Songs, Artists, Albums, Genres, Playlists, Liked and Folders. The
catalog is a database mirror of MediaStore, so the tabs and pages update
live when the device library changes. Artist, album, single and genre
pages assemble their track lists from the catalog; the artist page adds a
Recent Release card (the artist's newest album, single or EP) and a
Top Songs list with the artist's five most-played tracks.

Every track row carries the same track menu: Track Info, a Like toggle and
Add to Playlist. The Folders tab browses the folder tree directly and
respects the folder filter (see below).

## Home

The Home screen leads with one Wrapped card per period that has listening
activity — Today, Week, Month and Year — each summarizing that period's
total listening time and top song, artist and album; tapping a card opens
the Wrapped story for the period. Below the cards, Recently Added lists
the newest tracks in the catalog.

## Search

Search runs against the database and returns tracks, albums, artists,
genres, playlists and liked songs. A track result also surfaces the
playlists containing it; a liked song remains findable through its stored
snapshot even after its file leaves the catalog.

Opening or playing a result also records it as a recent search. While the
query is empty, the search screen lists those recents — most recent
first, with no size limit — as working shortcuts: a track plays, an artist, album,
genre or playlist opens its page, and opening one again moves it to the
top. Each entry can be removed individually, or the whole list cleared at
once. Recent searches live in a single small file in the app's cache
directory, never in the database, so clearing the app cache removes them
too.

## Player

The player screen shows the current track over a blurred artwork backdrop:
marquee title and artist, the current lyric line as a live preview, a
wavy seek bar, and the transport controls. The seek bar draws its wave
only while playback is running — it flattens when paused or while being
scrubbed — its thumb widens into a line during a scrub, and it ticks
haptically while dragging. A source line under the seek bar names the
output device, bit depth, sample rate, format and whether playback is
Bit-Perfect; tapping it opens the pipeline sheet, which traces the chain
Source → Decoder → Output → Device → Received for the current track.

The audio output indicator opens the output sheet: the current output
device and the media volume, or — in Direct USB mode — the DAC's own
hardware volume slider (see
[hardware volume](direct-usb/HARDWARE_VOLUME.md)). A mini player stays
available while browsing and mirrors the full player's state.

## Queue, repeat and shuffle

The queue sheet lists the active queue and jumps to any item. Repeat
(off, all, one) and shuffle live in the sheet's control group; both are
player-level state, they apply identically in every output mode, and the
playback service preserves them when it rebuilds the player for a mode
change.

## Lyrics

The lyrics view renders synchronized lyrics word by word: the active word
lifts and sweeps as it is sung, words held long enough grow letter by
letter with a matching glow, backing-vocal lines render smaller, and an
instrumental gap shows three dots riding a slow wave. Tapping a line
seeks to it. The list fades softly at its top and bottom edges.

Two tools sit in the lyrics menu:

- **Share** — long-pressing a lyric line enters picking mode (taps toggle
  lines, the list stops scrolling, up to 250 characters of lyric text).
  The picked lines render onto a share card with the track's artwork,
  title and artist, exported as a 1080-pixel-wide PNG: saved to
  `Pictures/Rovia` or shared through the system share sheet.
- **Offset** — shifts the lyrics against the audio from −5 to +5 seconds
  in 100 ms steps. The offset belongs to the current playback session of
  that track only: it is never written to storage or to the file, and it
  resets when the track changes.

## Playlists

Playlists are created, renamed and deleted from the Library's Playlists
tab and the playlist page; any track joins a playlist through its track
menu, and a track already in the playlist is not added twice. A
playlist's artwork is its own name rendered on a color derived from the
name. Playing a playlist queues the tracks whose files still resolve in
the catalog; entries whose files disappeared stay stored as snapshots
and remain visible to search.

## Liked songs and liked entities

Liking a track toggles it in the Liked collection, listed most recently
liked first under a hero taken from the newest liked track's artwork.
Artists, albums, singles and EPs carry their own Like button; the Liked
tab gathers the liked entities next to the liked songs entry, and each
one navigates back to its page.

## History and statistics

The playback service records every play — sessions, per-track events and
listening segments — and the History screen reads that activity back. A
play counts as qualified after at least 30 seconds or half the track,
whichever comes first; qualified counts drive the artist page's Top
Songs and the Wrapped rankings.

## Wrapped

Wrapped recaps a period — today, this week, this month or this year — as
a story: total listening time first, then top artists, top songs and top
albums, closing with a summary slide. Slides advance and rewind by
tapping the right or left of the screen. Any slide saves to
`Pictures/Rovia` or shares as a full-screen 1080×1920 PNG.

## External Player

Opening an audio file with Rovia from a file manager (Open With) starts
the External Player: a temporary, single-file player that presents the
same player screen as the regular player, in the same swipe-down sheet —
lyrics, track info, output and volume, and the queue sheet with repeat
and shuffle all work (the queue holds exactly the one file). The file
never enters the library or any database — no catalog entry, no history,
no statistics — and the folder filter does not apply to it, so a file
from a filtered folder still plays. Playback continues in the background
and on the lock screen; leaving the player, swiping its sheet down or
dismissing its task stops it for good. Two player-screen items stay out
by design: Like and Add to Playlist, because both would write the file
into the library database, and the pipeline sheet, whose output and
decoder data describe the regular player's session, not this one.

The two players hand over politely: if the regular player is playing
when an external file starts, it pauses — queue and position untouched —
and resumes when the external track ends or the external player closes.
Taking the regular player back manually cancels the automatic resume.
The technical contract is in
[the External Player document](media3/EXTERNAL_PLAYER.md).

## Folder filter

The folder filter hides chosen folders from the library: their tracks
leave the catalog views until the folders are allowed again. It affects
only library browsing and catalog playback; the External Player ignores
it by design.

## Settings

- **Audio** — the output mode selection (OFF, Native A14, Direct USB,
  DAP) with the active mode's status, and the switch that allows playback
  at a fixed USB output volume when a DAC's hardware volume cannot be
  controlled. A Bit-Perfect mode whose device is absent falls back to
  OFF with the reason shown.
- **Folder Filter** — the folder allow/hide list.
- **About** — app identity and version.
