# Media3 Version Notes

Rovia pins Media3 `1.12.0-alpha02` in `gradle/libs.versions.toml`. The
Bit-Perfect integration depends on specific Media3 contracts, so a
version change is a deliberate step, not a routine bump.

## Contracts the integration relies on

- `BaseRenderer` lifecycle (`onEnabled`, `onStreamChanged`,
  `onPositionReset`, `onStarted`, `onStopped`, `onDisabled`) and its
  stream-reading behavior, including `SampleStream.FLAG_PEEK` reads that
  do not advance the sample queue — the shadow-stream pacing of the
  Bit-Perfect renderers is built on this.
- `Renderer.getMediaClock()`: ExoPlayer adopts a renderer's non-null
  media clock as the player clock, with at most one enabled renderer
  providing one.
- ExoPlayer's reading-period advance rule (all renderers finished
  reading, next period prepared, remaining duration within the 10-second
  threshold), which the pacing is designed around.
- `MediaSession.setPlayer(...)` for swapping the player on a mode change
  without losing the session.
- `AnalyticsListener.onAudioDecoderInitialized(...)`, which provides the
  platform decoder name shown in the player's audio pipeline view for the
  OFF path.

## When upgrading

Update all Media3 artifacts together through the version catalog, read
the release and migration notes for every intervening release, and check
the contracts above against the new sources before building on them.
`UnstableApi` in this version is not a `@RequiresOptIn` annotation, so
opt-in markers have no effect on these APIs.
