# Murotal Audio Editor 0.2.0

Offline Android clip editor. Build: Gradle 8.9, JDK 17, Android SDK 35.

## Track and split controls
- New projects start without empty tracks. Add Audio creates a track up to five tracks.
- Add Track creates an empty track; names are Track 1 through Track 5.
- Each track offers Add Audio and Move selected clip here. At five tracks, global Add Audio appends to Track 5.
- Select a clip, tap or drag the ruler to position the red cursor, then press Gunting / Split.
- Select either resulting segment to delete or edit it.
- Zoom controls enlarge the shared proportional timeline; scroll horizontally to navigate.
- Split respects speed, source trim, volume, mute, and pitch.
- Selected-clip preview respects both trim boundaries.
- Existing populated tracks are preserved and renamed; old unused default tracks are removed during migration.
- Manual empty tracks created in version 0.2 persist across reopening.

## Validation
GitHub Actions runs unit tests for split boundaries and speed mapping, then builds the debug APK.

## Current limitations
Playback previews the selected clip only. Waveform, full multitrack mixing, export, drag-and-drop between tracks, undo/redo and fade are not implemented.
Projects autosave locally; this version has no portable project export.

