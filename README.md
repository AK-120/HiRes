# HiFi Player (MVP)

Minimal, local-first Android music player. Kotlin, Jetpack Compose, MVVM, Media3.

## Open and run
1. Open this folder in Android Studio (Ladybug or newer, JDK 17). It will sync Gradle and create the wrapper.
2. Run on a device or emulator with music files in shared storage.
3. Grant the audio and notification permissions when prompted.

Not compiled in the authoring environment (no Android SDK there), so expect to fix a few compile errors on first sync.

## Layout
- `music/`: `MusicProvider`, `LocalMusicProvider` (MediaStore), placeholder Apple Music and Spotify providers
- `metadata/`: on-demand technical metadata (codec, sample rate, channels, bit depth). Missing values stay null and show as "Bit depth unavailable"
- `playback/`: `PlaybackService` (ExoPlayer plus MediaSession, gives notification and background playback) and `PlaybackManager` (controller wrapper, queue, shuffle, repeat, volume)
- `usb/`: `UsbAudioMonitor` reports the connected USB output and its advertised sample rates
- `data/`: `LibraryRepository`, recently played store
- `ui/`: Now Playing (main screen), Library (Songs, Albums, Artists, Playlists, Recent), Sources

## Known limits (by design or still to do)
- No bit-perfect claim. Audio uses Android's normal stack. The USB line shows what the device advertises, not the active stream format.
- Bit depth is only shown when Android reports it (PCM encoding, or API 31+ metadata). Many FLAC files will show "Bit depth unavailable".
- Playlists tab is a placeholder (planned V1.1).
- Album art uses the legacy `content://media/external/audio/albumart` URI, which may return nothing on newer Android versions. Next step: load via `ContentResolver.loadThumbnail` or embedded art.
- If the app restarts while the service is still playing, the current track shows after the library loads and the track changes.
- Apple Music and Spotify are placeholders. When added, they must use only official SDKs and APIs.
