# Prism

A YouTube Music client for Android with a Liquid Glass look: a floating glass tab bar
with a separate round search button, a glass mini player, and colors that follow the
album art of the song that's playing.

Prism is based on [Metrolist](https://github.com/MetrolistGroup/Metrolist) (v13.7.0),
which itself builds on InnerTune and OuterTune. All the playback, library, lyrics and
account features come from Metrolist; Prism changes the name, icon and design.

## What Prism changes

- **Name and package:** the app is called Prism and installs as `com.lupijine.prism`,
  so it can sit next to Metrolist on the same phone.
- **Icon:** a white prism on a deep violet background.
- **Navigation bar:** `ui/component/AppNavigation.kt` draws a floating glass pill for
  Home, Library (and Listen Together), with Search as its own round glass button.
- **Mini player:** `ui/player/MiniPlayer.kt` uses a see-through glass pill by default.
- **Glass style:** `ui/theme/PrismGlass.kt` holds the shared glass surface
  (`Modifier.prismGlass`) and its dark/light colors.
- **Accent:** Prism violet (`#A99BFF`) is the default theme color, and is listed first
  in Settings › Appearance › Theme.
- **Updater:** turned off in every build flavor, so Prism never offers to install
  a Metrolist update.

## Building

```bash
./gradlew :app:assembleFossDebug
```

The APK lands in `app/build/outputs/apk/`. Requires JDK 21 and the Android SDK.

If YouTube Music isn't available in your region, you'll need a VPN or proxy set to a
supported region.

## License

GPL-3.0, the same as Metrolist. See [LICENSE](LICENSE). This project is not affiliated
with YouTube, Google LLC, or the Metrolist team.
