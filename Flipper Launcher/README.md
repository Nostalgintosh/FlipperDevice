# Flipper Launcher

A minimalist, terminal-first Android home screen styled after the **Flipper Zero** —
black chassis, orange backlight, monospace everywhere. Built for coders, tinkerers
and ethical hackers who'd rather type than tap.

```
┌─[ fzsh ]──────────────┐
│ FLIPPER ZERO // SHELL │
└───────────────────────┘
```

## Two parts + an app drawer

The launcher is a 2-page pager — **`>_ TERMINAL`** and **`</> CODE`**. Swipe left/right
or use the bottom tabs. The app drawer is a pop-up over the terminal, not a third page.

1. **`>_ TERMINAL`** — `fzsh`, the Flipper Zero shell. **Find, see and create**: search
   apps, launch them, read device info, and create aliases / notes / dock pins, all by
   command. Any result line is tappable.
2. **`</> CODE`** — a lightweight editor with multi-language syntax highlighting. Files
   live in the app's external dir, so Termux can compile them; `▶ run` saves, jumps to
   the terminal, and runs the file via the `sh` / `lx` bridges. From the terminal,
   `edit <file>` opens a file straight in the editor. The editor borrows the bits
   programmers like from Notepad++: a **quick-symbol row** above the keyboard
   (`{ } ( ) ; = " → …`), **auto-indent** and **auto-closing brackets**, and a status
   bar showing `Ln, Col`, length, and language. A **save-state line** runs down the left
   margin — **green when saved, red while you have unsaved edits**. The controls
   (`[=]` files · `▶ run` · `+ new` · `save`) sit in the thumb zone above the tabs.

Type **`-apps`** (or `drawer`) to pop up the **APPS A–Z** drawer: every installed app in
alphabetical order, with sticky letter headers and an A–Z fast-scroll rail down the right
edge (under your thumb). The drawer's own input live-filters the list — press enter to
launch the top match; ✕ or the back gesture closes it.

### Everything important sits under your thumb

The bottom third is the whole control surface — exactly where your thumb rests:

```
┌─────────────────────────────────┐
│ fz term  disk mem cpu net 12:30 │   ← STATUS  (live device vitals)
│                                 │
│      terminal  /  code          │   ← content   (-apps pops the drawer)
│                                 │
├─────────────────────────────────┤
│ [pinned] [pinned] [pinned] …    │   ← DOCK  (one-tap favorites)
│ fz> ___________________   ⏎     │   ← INPUT (runs commands; -apps opens drawer)
│ [ >_ TERMINAL ] [ </> CODE ]    │   ← TABS
└─────────────────────────────────┘
```

- **Status** — top bar shows live device vitals in the middle: `disk` (storage used),
  `mem` (RAM used), `cpu` (busy %, or current GHz where the kernel hides it), and `net`
  (wifi / cell / eth / off, red when there's no validated internet). Clock + battery sit
  on the right. No runtime permissions — only the install-time `ACCESS_NETWORK_STATE`.
- **Dock** — pinned apps for one-tap launch. Long-press a dock icon to unpin.
- **Input** — runs commands on the terminal; `-apps` pops the A–Z drawer (which has its
  own filter).
- **Tabs** — inverted-selection segmented control, Flipper-style.

## `fzsh` command reference

Type `help` in the terminal for the live index, or `man <cmd>` for detail.

| Command | What it does |
|---|---|
| `help` | command index |
| `common` | quick cheat sheet for the most useful launcher commands |
| `ls` / `apps` | list every app (A–Z) inline, tap to launch |
| `-apps` / `drawer` | pop up the full A–Z app drawer (✕ or back to close) |
| `find <q>` | search apps (`search`, `grep`, `f`) |
| `open <q>` | launch best match (`run`, or `./<q>`) |
| `info <q>` | package name + version |
| `create alias <name>=<cmd>` | make a shortcut (e.g. `create alias g=open chrome`) |
| `create note <text>` | save a quick scratch note |
| `create pin <app>` | add an app to the dock |
| `pin` / `unpin <q>` | manage the thumb dock |
| `fav` | list pinned apps |
| `alias` / `unalias` | list / set / remove aliases |
| `note` / `note ls` / `note rm <i>` | scratchpad for IPs, payloads, snippets |
| `battery` | battery bar |
| `sysinfo` / `neofetch` | device readout with ASCII dolphin |
| `flipper` | Flipper Zero panel — opens the companion app if installed |
| `degoogle` / `privacy` | safe de-Google checklist and settings links |
| `tools` | curated Flipper/dev links |
| `settings` | Android settings (`wifi`, `bt`, `loc`, `dev`) |
| `uninstall <q>` / `rm <q>` | request app removal |
| `theme <name>` | accent: `orange green amber red cyan mono` |
| `-pp` / `pwsh` | PowerShell mode — blue terminal that relays input to the `win` SSH host (`-fz` / `exit` returns) |
| `edit <file>` / `code` | open a file in the `</> CODE` editor (`nano`, `vim`, `vi`) |
| `pwd` / `cd` / `cat` | pseudo-shell navigation for launcher areas |
| `termux` / `bash` / `pkg` / `ssh` / `curl` | hand off real Unix commands to Termux |
| `clear` / `history` / `echo` / `date` / `whoami` | shell basics |

Aliases expand before running, so `create alias k=find` lets you type `k term`.

## Build & install

Requirements: Android Studio (bundles a JDK) **or** JDK 17+ and the Android SDK
(platform 35, build-tools 35).

```bash
# from the project root
./gradlew :app:assembleDebug          # build the APK
./gradlew :app:installDebug           # build + install to a connected device/emulator
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`. Or just open the folder
in Android Studio and hit **Run**.

### Set it as your home screen

After installing, press the **Home** button → pick **Flipper Launcher** → *Always*.
(Or **Settings → Apps → Default apps → Home app → Flipper Launcher**.)
To revert, set a different Home app the same way.

## Design notes

- **Tech:** Kotlin + Jetpack Compose, single `Activity`, `minSdk 26`, `compileSdk 36`.
- **No network, no tracking.** Reads only the launchable-app list (no `QUERY_ALL_PACKAGES`)
  and a battery broadcast. Pins/aliases/notes/theme persist in `SharedPreferences`.
- **Fonts:** two families, by design. **`Pixel`** = *Silkscreen* (OFL, bundled in
  `res/font/`) drives the chrome — status bar, tabs, section letters, the A–Z bubble.
  **`Mono`** = system monospace drives the terminal body, because pixel fonts lack the
  box-drawing/block glyphs (`█ ░ ┌ ─ │ ✓`) the shell uses. Both are set in
  [`ui/theme/Theme.kt`](app/src/main/java/com/a11/flipperlauncher/ui/theme/Theme.kt);
  swap `Pixel` for another TTF (e.g. *HaxrCorp 4089*) to retune the look.

## Layout

```
app/src/main/java/com/a11/flipperlauncher/
├── MainActivity.kt              # HOME activity, battery + package receivers
├── data/                        # AppRepository, AppInfo, Prefs
├── terminal/                    # fzsh engine + command models
├── vm/LauncherViewModel.kt      # state, action dispatch, persistence
└── ui/                          # theme, widgets, Terminal/AppDrawer/CodeEditor screens, LauncherRoot
```

## Credits

- *Silkscreen* by Jason Kottke — SIL Open Font License, full text in
  [`licenses/Silkscreen-OFL.txt`](licenses/Silkscreen-OFL.txt).

— *The A11 Project*
