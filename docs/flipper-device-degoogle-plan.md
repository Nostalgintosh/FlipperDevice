# Flipper Device de-Google plan

Goal: reduce Google surface area on the Galaxy A11 without breaking core phone
behavior, app compatibility, WebView, push notifications, or Play updates.

## Safe order

1. Run an audit first:

   ```bash
   tools/flipper_device_manager.sh audit
   ```

2. Rename the device:

   ```bash
   tools/flipper_device_manager.sh rename
   ```

3. Preview package disables:

   ```bash
   tools/flipper_device_manager.sh degoogle
   ```

4. Apply only the non-core Google app disables:

   ```bash
   tools/flipper_device_manager.sh degoogle --apply
   ```

5. Only if you accept feature loss, preview optional disables:

   ```bash
   tools/flipper_device_manager.sh degoogle --include-optional
   ```

## Core packages to keep

Keep these unless the phone is being moved to a custom ROM or a fully
Google-free Android stack:

- `com.google.android.gms` - Google Play services. Many apps use this for push,
  account auth, location APIs, anti-abuse checks, and in-app services.
- `com.google.android.gsf` - Google Services Framework.
- `com.android.vending` - Play Store. Keep it if you want normal app updates.
- `com.google.android.webview` - Android System WebView. Many apps need this to
  render login, help, and payment screens.
- `com.google.android.configupdater`, `com.google.android.ext.services`,
  `com.google.android.ext.shared`, `com.google.android.modulemetadata`,
  `com.google.android.networkstack`, `com.google.android.permissioncontroller`.

## First pass disables

The manager script targets consumer Google apps first: YouTube, YouTube Music,
Google TV, Photos, Drive/Docs/Sheets/Slides, Gmail, Meet/Duo, Podcasts, Books,
Keep, Wallet, Google Home, feedback, print recommendation, Android Auto, and
ARCore. These are reversible with:

```bash
tools/flipper_device_manager.sh restore-google-apps
```

## Optional disables

The `--include-optional` set touches apps that may be important depending on
how the phone is used: Chrome, Maps, Calendar, Google Messages, Contacts, Files
by Google, Digital Wellbeing, Google backup/restore, setup helpers, and Google
calendar/contact sync adapters.

Do not use the optional set until the audit confirms there is a Samsung or
FOSS replacement for the same job.

## Expansion path

- Install `Flipper Launcher` as the home screen.
- Install `HackerBoard` as the default keyboard for terminal and device-control
  work.
- Add F-Droid and Termux for a local, Google-independent toolchain.
- Use `wgt -termux` inside Flipper Launcher after installing Termux:Widget to
  expose one-tap scripts in the launcher terminal.
