# FlipperDevice

FlipperDevice is an Android-based companion stack for turning a Galaxy A11 into
a focused Flipper Zero-style field device. It combines a terminal-first home
screen, a programmer-oriented soft keyboard, and ADB tooling for configuring a
dedicated device profile.

The project is free and open source, built for learning, development, repair,
authorized security work, and day-to-day device control.

## What is included

| Path | Purpose |
| --- | --- |
| `Flipper Launcher/` | Kotlin + Jetpack Compose Android launcher with a Flipper-inspired terminal shell, app drawer, code editor, pinned app dock, live system status, Termux handoff, and privacy/device shortcuts. |
| `HackerBoard/` | Android IME for coders and terminal users with number row, Tab, Esc, Ctrl/Alt/Shift, arrows, function keys, symbol layers, snippets, and Flipper-style themes. |
| `tools/flipper_device_manager.sh` | ADB helper for auditing the phone, renaming it to `Flipper Device`, installing the APKs, setting the launcher as Home, and applying reversible de-Google package changes. |
| `docs/flipper-device-degoogle-plan.md` | Safe de-Google plan with core packages to keep, first-pass disables, optional disables, and rollback guidance. |
| `reports/` | Device audit output and UI verification artifacts from the current Galaxy A11 setup. |

## Project goals

- Make the phone feel like a compact Flipper-style command console.
- Keep common Android app access available through a fast launcher and search.
- Give terminal and code workflows the missing mobile keyboard keys.
- Support a cautious, reversible reduction of nonessential Google apps.
- Keep the phone usable: WebView, Play services, Play Store, network stack, and
  other core compatibility packages are intentionally preserved.

## Flipper Launcher

`Flipper Launcher` replaces the default Android home screen with a two-page
workspace:

- `>_ TERMINAL` runs `fzsh`, a launcher shell for searching apps, opening apps,
  creating aliases, saving notes, pinning dock apps, checking system state,
  opening Android settings, and handing real commands to Termux.
- `</> CODE` provides a lightweight code editor with syntax highlighting,
  quick-symbol keys, auto-indent, auto-closing brackets, save state indicators,
  and a run action that routes through the terminal/Termux bridge.
- The A-Z app drawer opens over the terminal with `-apps` or `drawer`.
- The bottom control area keeps pins, terminal input, and tabs under the thumb.

See [`Flipper Launcher/README.md`](Flipper%20Launcher/README.md) for the full
launcher command reference and design notes.

## HackerBoard

`HackerBoard` is a custom Android keyboard for shell and code work. It restores
keys that normal mobile keyboards hide:

- Permanent number row
- `Tab`, `Esc`, `Ctrl`, `Alt`, `Shift`
- Arrow keys, Home/End, PgUp/PgDn, Ins/Del
- Function row `F1` through `F12`
- Symbol and function layers
- Auto-paired brackets and quotes
- Shell snippet strip for common tokens and commands
- Flipper, Matrix, Cyan, Amber, and Magenta themes

See [`HackerBoard/README.md`](HackerBoard/README.md) for setup, layout, and IME
details.

## Device manager

The manager script expects `adb` and a connected Android device with USB
debugging enabled.

```bash
tools/flipper_device_manager.sh audit
tools/flipper_device_manager.sh rename
tools/flipper_device_manager.sh install
tools/flipper_device_manager.sh set-home
tools/flipper_device_manager.sh degoogle
```

The de-Google command previews changes by default. Use `--apply` only after
reviewing the package list:

```bash
tools/flipper_device_manager.sh degoogle --apply
```

To restore the first-pass Google app disables:

```bash
tools/flipper_device_manager.sh restore-google-apps
```

Read [`docs/flipper-device-degoogle-plan.md`](docs/flipper-device-degoogle-plan.md)
before disabling packages.

## Build requirements

- Android Studio, or JDK 17+ with the Android SDK installed
- Android SDK platform 36
- ADB for device install/setup

On this machine, Gradle builds use the Android Studio JBR:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

## Build Flipper Launcher

```bash
cd "Flipper Launcher"
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
./gradlew :app:testDebugUnitTest
```

The debug APK is written to:

```text
Flipper Launcher/app/build/outputs/apk/debug/app-debug.apk
```

## Build HackerBoard

```bash
cd HackerBoard
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
```

The debug APK is written to:

```text
HackerBoard/app/build/outputs/apk/debug/app-debug.apk
```

## Install on a device

Build both apps, connect the phone with USB debugging enabled, then run:

```bash
tools/flipper_device_manager.sh install
tools/flipper_device_manager.sh set-home
```

To use HackerBoard, open the app after install, enable it in Android keyboard
settings, then switch the active keyboard to HackerBoard.

## Verified status

The current workspace has been checked with:

```bash
cd "Flipper Launcher"
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest

cd ../HackerBoard
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug :app:lintDebug
```

Both projects pass those checks in the current import.

## Repository layout

```text
.
+-- Flipper Launcher/
|   +-- app/src/main/java/com/a11/flipperlauncher/
|   +-- app/src/main/res/
|   +-- README.md
+-- HackerBoard/
|   +-- app/src/main/java/com/a11/hackerboard/
|   +-- app/src/main/res/
|   +-- README.md
+-- docs/
|   +-- flipper-device-degoogle-plan.md
+-- reports/
+-- tools/
|   +-- flipper_device_manager.sh
+-- LICENSE
+-- README.md
```

## Privacy and safety

FlipperDevice is intended for owned devices and authorized work. The launcher
does not add tracking, analytics, or a network backend. HackerBoard is an input
method that sends standard key events and text through Android's normal IME
interfaces. The device manager uses ADB commands that are visible and
reversible where practical.

Do not disable core Android or Google packages without understanding the impact.
The documented safe path keeps compatibility-critical packages installed.

## License

This repository is licensed under the MIT License. The bundled Silkscreen font
is licensed separately under the SIL Open Font License; see
[`Flipper Launcher/licenses/Silkscreen-OFL.txt`](Flipper%20Launcher/licenses/Silkscreen-OFL.txt).
