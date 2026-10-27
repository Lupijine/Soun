<p align="center">
  <img src="app/src/main/res/drawable-nodpi/soun_launcher.png" alt="Soun app icon" width="144">
</p>

<h1 align="center">Soun</h1>

<p align="center">A YouTube Music client for Android, with a translucent, music-inspired interface.</p>

Soun is based on [Metrolist](https://github.com/MetrolistGroup/Metrolist), which builds on InnerTune and OuterTune. It brings its own visual identity to music playback, playlists, lyrics, and your library.

## Features

- A floating mini player and navigation bar with glass-inspired surfaces.
- Playlists, a listening queue, and a library with liked, downloaded, and on-device audio.
- Synchronized lyrics, theme options, and colors inspired by your music.
- Offline downloads and account synchronization.
- Playlist cover customization, with an on-device fallback when a YouTube upload is unavailable.

## Build

Soun requires JDK 21 and the Android SDK. To build the FOSS debug APK:

```bash
./gradlew :app:assembleFossDebug
```

The APK is written to `app/build/outputs/apk/foss/debug/`. The debug package is `com.lupijine.soun.debug`; the release package is `com.lupijine.soun`. Release builds require an appropriate signing key before they can be installed or distributed.

The [Build Soun APK workflow](https://github.com/Lupijine/Soun/actions/workflows/build-soun.yml) signs the release with the repository secrets `KEYSTORE` (base64-encoded keystore), `KEY_ALIAS`, `KEYSTORE_PASSWORD`, and `KEY_PASSWORD`. Run it from GitHub Actions and download `soun.apk` from [Releases](https://github.com/Lupijine/Soun/releases).

## Credits and license

Soun is distributed under the [GNU General Public License v3.0](LICENSE). Thanks to the Metrolist, InnerTune, and OuterTune contributors. Many interface icons are based on [Lucide](https://lucide.dev), which is distributed under the ISC license.

Soun is not affiliated with YouTube, Google, or the Metrolist team. YouTube Music availability depends on your region.
