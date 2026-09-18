# Murotal Audio Editor 0.3.0

Offline Android audio editor. Build with JDK 17, Gradle 8.9, Android SDK 35; minimum Android 8.

## Playback
- Putar semua plays all unmuted tracks from the red playhead.
- Track terpilih plays all clips on the selected clip's track.
- Jeda pauses at the current position; press the desired mode to resume.
- If the playhead is at the end, playback restarts from zero.
- Preview coordinates independent Media3 players with a shared wall clock and buffering pauses. It is not sample-accurate studio synchronization. Export uses a fixed sample grid.

## Files and export
- Simpan proyek creates a .mae JSON project file in the location chosen in Android's file picker.
- Buka proyek restores its layers, trims, positions, volume, speed, pitch and mute state.
- Projects reference original local audio; they do not bundle it. Keep original audio available. Missing/inaccessible media is reported without replacing the active project.
- Autosave remains internal and uses atomic writes.
- Ekspor mixes all tracks into WAV (16-bit stereo 44.1 kHz) or M4A/AAC (128/192/320 kbps).
- Export applies trim, timing, speed, pitch, volume and mute. Muted clips retain project duration as silence.
- Export runs in a foreground service with progress and cancellation; it decodes bounded buffers to temporary disk PCM, then sums in 4096-frame blocks. It checks free disk space before starting and cleans temporary data afterward.
- WAV is limited to 4 GB. Export accepts mono/stereo decoded PCM. Sum peaks are clipped to the 16-bit range; lower track volume to avoid overload.
- MP3 and FLAC export are not implemented. They are not shown as working export choices.

## Editing
- Up to five generically named tracks; choose the destination when adding audio.
- Hold a clip then drag horizontally for time or vertically to another existing track. Occupied positions snap forward to prevent overlap.
- Dragging beyond the visible viewport does not auto-scroll; zoom out or scroll first.
- Tap/drag the ruler to set the playhead, then Gunting / Split; select the segment to edit/delete.
- Move/duplicate buttons append after the last clip. Delete track asks confirmation when occupied and never deletes original files.

## Tests
GitHub Actions runs unit tests and builds a debug APK. Android 10 emulator tests render two overlapping generated tones, verify mixed amplitude and duration, round-trip a project, produce AAC output, exercise trim/speed/pitch, and check playback completion/mode switching.

## Still pending
Waveform, fade, undo/redo, bundled portable projects, MP3/FLAC encoders and automatic edge scrolling.


## Version 0.4.0

Compact editor with project menu, per-track menu, tool grid and contextual sliders.
Transport offers all tracks or the selected track. MP3 and M4A offer 128/192/320 kbps;
WAV remains stereo 16-bit 44.1 kHz. Export reuses decoded PCM for identical clips,
uses bulk mono/stereo writes, and reduces codec polling delays and buffer allocations.
Long recordings still require decoding, mixing and encoding; device speed and storage
will affect export time. Project files remain editable `.mae` JSON references.

### Native MP3 source and rebuilding

LAME core is LGPL-2.0-or-later, dynamically linked as `libmp3lame.so`.
Notices and full license are in `app/src/main/assets` and accessible from the app menu.
CMake fetches the exact source commit documented in `LAME-NOTICE.txt`; it does not
compile the upstream GPL Java/JNI wrapper. Our JNI bridge is in `app/src/main/cpp`.
To rebuild or relink with a modified LAME, install JDK 17, Gradle 8.9, Android SDK 35,
NDK 27.0.12077973 and CMake 3.22.1. Clone this repository, replace `LAME_DIR` in
CMakeLists.txt with the path to your modified core (and omit FetchContent if desired),
then run `gradle :app:assembleDebug`. The resulting APK includes the rebuilt shared
library and can be signed and installed normally. Reverse engineering for debugging
modifications to the LGPL library is permitted. CI also publishes the corresponding
unmodified core source alongside the APK.
