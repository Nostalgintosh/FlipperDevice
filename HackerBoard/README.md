# HackerBoard

A minimal Android soft keyboard (IME) for coders, sysadmins, and ethical
hackers — the keys a physical keyboard has that phone keyboards usually drop,
plus a scrolling shell‑snippet strip.

> Dark, monospace, zero third‑party dependencies (framework APIs only).

## Why it exists

Stock phone keyboards bury everything a programmer needs: no number row, no
`Tab`, no `Esc`, no `Ctrl`, no arrow keys, no function keys, brackets two layers
deep. HackerBoard puts them back.

## Features

**Physical‑keyboard parity (the stuff phones drop):**
- **Permanent number row** with shifted symbols on long‑press (`1`→`!` … `0`→`)`)
- **Tab** and **Esc** as real keys (essential for shells & vim)
- **Ctrl / Alt / Shift** as latching modifiers — tap = one‑shot, tap again =
  lock, tap again = off
- **Arrow cluster** ◀ ▲ ▼ ▶ plus **Home / End / PgUp / PgDn / Ins / Del**
- **Function row F1–F12**
- **Key repeat** when you hold backspace or an arrow
- **Real key events**, not just text: `Ctrl+C`/`Ctrl+D` work in Termux,
  `Shift+Arrow` selects, `Ctrl+Arrow` jumps by word, `Shift+Tab` back‑tabs
- Optional **haptic** + **key‑click sound**

**Coder essentials:**
- **Swipe‑to‑corner symbols** (Unexpected‑Keyboard style): each letter carries
  symbols in its corners — swipe ↖↗↙↘ to type them. Top row → digits (↗) and
  their shifts (↖); home row → every bracket/operator (` ` ~ ( ) [ ] { } < > - _ = + / \ | & `);
  bottom row → quotes & punctuation. A normal tap still types the letter.
- A dedicated **SYM layer** with every glyph (`~ \` | \ / { } [ ] < > …`) one tap away
- **Auto‑paired** brackets/quotes that drop the caret inside: `()` `[]` `{}` `""` `''` `<>`
- Multi‑char operators: `->` `=>` `&&` `||`
- Home‑row **Ctrl** (Caps‑Lock position) for terminal muscle memory

**Themes:** **Flipper** (default — Flipper‑Zero black + orange `#FF8200`),
Matrix (green), Cyan, Amber, Magenta. Pick one on the setup screen.

**Ethical‑hacker snippet strip (scrollable, above the keys):**
- Shell tokens: `|  ~/  ./  ../  &&  ||  >  >>  2>&1  $()  ${}  *  -  --  #`
- Command names: `sudo ssh scp cd ls -la cat grep chmod +x curl wget nmap nc python3 git ping ip a ss -tulpn`
- Addresses/ports/encodings: `127.0.0.1  0.0.0.0  localhost  :  /24  0x  \x`
- Quick **paste** and **switch‑keyboard** chips

**Three layers:** `ABC` (letters + number row) · `SYM` (symbols/operators) ·
`Fn` (function keys + navigation). Switch with the `SYM` / `Fn` / `ABC` keys.

## Layout at a glance

```
ABC   esc 1 2 3 4 5 6 7 8 9 0          SYM   ! @ # $ % ^ & * ( ) ⌫
      tab q w e r t y u i o p                ~ ` | \ / - _ = + ?
      ctrl a s d f g h j k l                { } [ ] < > : ; " '
      ⇧ z x c v b n m , . / ⌫              () [] {} "" '' <> -> => && ||
      SYM Fn [ space ] ← ↑ ↓ → ⏎          ABC ctrl [ space ] ← → ⏎
```

## Build

Everything pins to versions already on this machine (offline‑friendly):
AGP 8.12.0 · Kotlin 2.0.21 · Gradle 8.14.5 · compileSdk 36 · minSdk 24.

```bash
./gradlew :app:assembleDebug      # APK → app/build/outputs/apk/debug/
./gradlew installDebug            # install on a connected device/emulator
```

Or just open the folder in Android Studio and Run.

## Enable it

1. Launch **HackerBoard** → tap **Enable in Settings**, toggle it on.
2. Tap **Switch Keyboard**, pick HackerBoard.
3. Use the test field to try it. Accent color, haptics, sound, and key height
   are adjustable on the same screen.

## Layout of the code

| File | Role |
|------|------|
| `HackerBoardService.kt` | The IME: view assembly, modifiers, `InputConnection` dispatch |
| `KeyboardView.kt` | Custom Canvas view — drawing, touch, repeat, long‑press |
| `Keys.kt` | Key/layer data model + the three layouts |
| `Snippets.kt` | The scrolling shell‑snippet strip |
| `Theme.kt` | Colors, accent palette, prefs |
| `SetupActivity.kt` | Enable/select walkthrough + settings |

## Scope / ethics

HackerBoard is just a keyboard: it inserts text and dispatches standard key
events. The “hacker” snippets are ordinary, ubiquitous command **names**
(`ssh`, `curl`, `nmap`, `nc`, …) — convenience shortcuts, not exploits. Use it
for your own systems and authorized engagements.
