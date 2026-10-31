# Helgeland

A calm Android home screen launcher built around Kristofer Haugvik's flat “Sør-Helgeland”
landscapes. The wallpaper is drawn by the launcher itself, and everything on top of it
(clock, grid, search bar, media line) is placed to fit the scene's bands.

## Features

- **Home:** hand-lettered clock (Indie Flower) with date, temperature and the next alarm;
  a 4×3 grid with drag and drop that centres itself when outer rows are empty
- **Scenes:** four wallpapers to choose from, each with measured areas for the grid and
  the media line
- **Stack (app drawer):** recently used apps, A–Z sections with an alphabet strip, usage-aware
  ordering, search with a web-search fallback, hiding apps
- **Icons:** global or per-app style: original, grayscale, the app's own monochrome icon,
  any installed icon pack (auto-matched or hand-picked icon), or a custom picture
- **Search bar:** grid item spanning 2 or 4 slots; opens Google search, optional mic for
  voice search
- **Media:** “now playing” line with play/pause and skip; tapping the title opens the player
- **Settings:** grouped into categories (home, clock & weather, stack, icons, gestures,
  general), including swipe-down and double-tap actions and vibration

## Building

Requirements:

- a JDK that includes `javac` (Gradle 9 needs Java 17 or newer; this project is built with Java 26)
- Android SDK with platform `android-37.2`; point `local.properties` at it
  (`sdk.dir=/path/to/Android/Sdk`)

```sh
./gradlew assembleRelease testDebugUnitTest
adb install -r app/build/outputs/apk/release/app-release.apk
```

The release build is minified and signed with the debug key, so it installs over a debug
build directly.

## Permissions

| Permission | Why |
|---|---|
| Coarse location | temperature for the clock line (Open-Meteo, no account) |
| Internet | the weather request |
| Notification access (optional) | reading media sessions for the media line; notifications themselves are never read |
| Expand status bar | swipe down to open notifications |
| Request delete packages | “Uninstall” in the app menu |

## Project layout

| File | Contents |
|---|---|
| `MainActivity.kt` | state, persistence and all actions the UI can trigger |
| `Launcher.kt` | home screen, grid, clock, search bar, media line, stack |
| `SettingsPopup.kt` | categorised settings |
| `IconPicker.kt` | popup container, per-app icon picker, pack browser |
| `AppMenu.kt` | long-press menus and their line glyphs |
| `LauncherSettings.kt` | settings model and scene definitions |
| `GridSlots.kt` | pure grid logic (moving, spanning, centring), unit-tested |
| `IconPack.kt`, `IconRender.kt`, `IconProvider.kt` | icon pack parsing, rendering, per-app style resolution |
| `Media.kt` | media session watcher |
| `Weather.kt` | Open-Meteo request |

## Licenses

See [`licenses/`](licenses/README.md). The wallpapers are not licensed for redistribution;
keep this repository private or remove them before publishing.
