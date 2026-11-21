# External Player

The External Player plays a single audio file handed to Rovia by another
app — typically a file manager's Open With — without involving the
library. It is a second, fully separate Media3 playback stack next to the
regular player, built from three pieces:

| Piece | Role |
| --- | --- |
| `ExternalPlayerActivity` | The `ACTION_VIEW` entry point; hosts only the player screen. |
| `ExternalPlaybackService` | A dedicated `MediaSessionService` with its own ExoPlayer and MediaSession. |
| `ExternalPlaybackCoordinator` | The pause/resume handshake with the regular player. |

## Entry and addressing

The activity declares an `ACTION_VIEW` intent filter for `audio/*` (plus
`application/ogg`) over the `content` and `file` schemes, runs as a
single task with its own task affinity, and reads the handed URI
directly — the calling app's temporary URI grant is the only permission
involved, and no library permission is requested. Title, artist, album
and duration come from the file's own metadata, falling back to the
display name; artwork resolves from the file itself, exactly as it does
for catalog tracks.

Controllers address a session by service class: `PlaybackController`
takes the target `MediaSessionService` class (the regular
`PlaybackService` by default), and the external activity passes
`ExternalPlaybackService`, so the two stacks never share a controller,
a queue or a session.

## Isolation guarantees

The external service is an ExoPlayer on the standard path — Symphonia
decoding (id `symphonia-rust`, the same standard coordinator as OFF),
automatic audio focus handling, the standard media notification — with
no reference to the database, the catalog, the statistics recorder or
the folder filter:

- Nothing is written anywhere: no catalog row, no playback session, no
  track event, no history entry. History, Top Songs and Wrapped only
  ever see the regular service's playback.
- The folder filter is never consulted; a file inside a filtered folder
  plays.
- The saved output mode does not apply; external playback always uses
  the standard path, and the Bit-Perfect coordinators belong to the
  regular service alone.
- Playback resumption is not implemented, so the system never offers to
  restart an external file later.
- The player screen runs in its external mode with full feature parity —
  lyrics, track info, queue, repeat, shuffle — except the items that
  would break the isolation: Like and Add to Playlist are hidden (both
  write to the library database), the pipeline sheet is hidden (its
  output and decoder data describe the regular player's session), and
  the output sheet always shows the system media volume instead of the
  regular session's DAC volume.

## Lifecycle

Background playback follows the standard MediaSessionService model: the
service owns the player, the media notification and the lock-screen
controls, so playback continues with the screen off. The temporary
nature is enforced at the exits:

- **Back / leaving the activity** — the activity stops and clears the
  external player, ends the coordinator session and stops the service.
  The player screen is hosted in a `ModalBottomSheet` with the same
  configuration as the main app's player sheet, so swiping the sheet
  down dismisses it into the same exit path.
- **Task dismissed from Recents** — the service's `onTaskRemoved` calls
  `pauseAllPlayersAndStopSelf()`, but only when the removed task's root
  intent belongs to the external activity; dismissing the main app's
  task leaves external playback running.
- **Track ends** — the session ends (see the handshake below); the
  screen stays open on the finished track.
- **Process death** — everything ends together; nothing was persisted,
  so nothing is restored.

A configuration change neither restarts nor replaces the playing file:
the recreated activity finds the same URI already loaded in the service
and leaves it alone.

## The pause/resume handshake

Audio focus alone cannot produce the required behaviour: when the
external player takes focus, the regular player receives a permanent
focus loss, and a permanent loss is never followed by a regain callback.
The handshake is therefore explicit, in `ExternalPlaybackCoordinator`,
which works because both players live in the same process:

1. The regular `PlaybackService` registers its current player with the
   coordinator every time it (re)builds one, and unregisters on destroy.
2. When the external player starts playing, the coordinator pauses the
   regular player if it is playing — a pause, never a stop: queue,
   position and mode are untouched — and remembers that it did so.
3. When the external session ends (track ended, activity exit, service
   teardown), the coordinator resumes the regular player if — and only
   if — it was playing at takeover time.
4. If the user plays the regular player manually while the external
   session is open, a guard listener cancels the pending resume; the
   user has taken the regular player back.
