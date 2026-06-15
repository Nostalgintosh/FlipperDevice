#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
DEFAULT_ADB="/Users/nostalgintosh/Library/Android/sdk/platform-tools/adb"
DEFAULT_JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

if [[ -x "${ADB:-}" ]]; then
  ADB_BIN="$ADB"
elif [[ -x "$DEFAULT_ADB" ]]; then
  ADB_BIN="$DEFAULT_ADB"
else
  ADB_BIN="adb"
fi

DEVICE_NAME="${DEVICE_NAME:-Flipper Device}"
JAVA_HOME="${JAVA_HOME:-$DEFAULT_JAVA_HOME}"
INSTALLED_PACKAGES=""

CORE_GOOGLE_KEEP="
com.android.vending
com.google.android.gms
com.google.android.gsf
com.google.android.webview
com.google.android.configupdater
com.google.android.ext.services
com.google.android.ext.shared
com.google.android.modulemetadata
com.google.android.networkstack
com.google.android.networkstack.tethering
com.google.android.permissioncontroller
"

SAFE_GOOGLE_DISABLE="
com.google.android.googlequicksearchbox
com.google.android.apps.googleassistant
com.google.android.youtube
com.google.android.apps.youtube.music
com.google.android.videos
com.google.android.apps.photos
com.google.android.apps.docs
com.google.android.apps.docs.editors.docs
com.google.android.apps.docs.editors.sheets
com.google.android.apps.docs.editors.slides
com.google.android.gm
com.google.android.apps.tachyon
com.google.android.apps.meetings
com.google.android.apps.podcasts
com.google.android.apps.magazines
com.google.android.apps.books
com.google.android.keep
com.google.android.apps.walletnfcrel
com.google.android.apps.subscriptions.red
com.google.android.apps.chromecast.app
com.google.android.apps.youtube.kids
com.google.android.feedback
com.google.android.printservice.recommendation
com.google.android.projection.gearhead
com.google.ar.core
"

OPTIONAL_GOOGLE_DISABLE="
com.android.chrome
com.google.android.apps.maps
com.google.android.calendar
com.google.android.apps.messaging
com.google.android.contacts
com.google.android.apps.nbu.files
com.google.android.apps.wellbeing
com.google.android.apps.restore
com.google.android.backuptransport
com.google.android.onetimeinitializer
com.google.android.partnersetup
com.google.android.syncadapters.calendar
com.google.android.syncadapters.contacts
"

usage() {
  cat <<'USAGE'
Usage:
  tools/flipper_device_manager.sh audit
  tools/flipper_device_manager.sh rename
  tools/flipper_device_manager.sh install
  tools/flipper_device_manager.sh set-home
  tools/flipper_device_manager.sh degoogle [--apply] [--include-optional]
  tools/flipper_device_manager.sh restore-google-apps

Defaults:
  DEVICE_NAME="Flipper Device"
  ADB=/Users/nostalgintosh/Library/Android/sdk/platform-tools/adb
  JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home

Safety:
  degoogle is a dry run unless --apply is present.
  It keeps Google Play services, Play Store, Google Services Framework, WebView,
  NetworkStack, permission controller, and config updater.
USAGE
}

adb() {
  "$ADB_BIN" "$@"
}

adb_shell_escaped() {
  local command="" arg escaped
  for arg in "$@"; do
    printf -v escaped "%q" "$arg"
    command+="$escaped "
  done
  adb shell "$command"
}

ensure_device() {
  local state
  state="$(adb get-state 2>/dev/null || true)"
  if [[ "$state" != "device" ]]; then
    adb devices -l || true
    cat >&2 <<'ERR'

No authorized adb device is ready.
Unlock the phone, accept "Allow USB debugging?", then run this command again.
ERR
    exit 2
  fi
}

load_packages() {
  INSTALLED_PACKAGES="$(adb shell pm list packages --user 0 | tr -d '\r' | sed 's/^package://')"
}

package_installed() {
  printf '%s\n' "$INSTALLED_PACKAGES" | grep -qx "$1"
}

try_setting_put() {
  local table="$1"
  local key="$2"
  if adb_shell_escaped settings put "$table" "$key" "$DEVICE_NAME" >/dev/null 2>&1; then
    printf 'set %s.%s = %s\n' "$table" "$key" "$DEVICE_NAME"
  else
    printf 'could not set %s.%s on this build\n' "$table" "$key"
  fi
}

cmd_audit() {
  ensure_device
  local report_dir report
  report_dir="$ROOT_DIR/reports"
  mkdir -p "$report_dir"
  report="$report_dir/$(date +%Y%m%d_%H%M%S)_flipper_device_audit.txt"

  {
    echo "Flipper Device audit"
    echo "generated: $(date)"
    echo
    echo "== adb devices =="
    adb devices -l
    echo
    echo "== identity =="
    for prop in \
      ro.product.manufacturer ro.product.model ro.product.name ro.product.device \
      ro.build.version.release ro.build.version.sdk ro.build.display.id ro.bootloader
    do
      printf '%s=%s\n' "$prop" "$(adb shell getprop "$prop" | tr -d '\r')"
    done
    echo
    echo "== current names =="
    for item in "global device_name" "system device_name" "secure bluetooth_name" "global wifi_p2p_device_name"; do
      table="${item%% *}"
      key="${item#* }"
      printf '%s.%s=%s\n' "$table" "$key" "$(adb shell settings get "$table" "$key" | tr -d '\r')"
    done
    echo
    echo "== battery =="
    adb shell dumpsys battery | tr -d '\r'
    echo
    echo "== disabled packages =="
    adb shell pm list packages -d --user 0 | tr -d '\r' | sort
    echo
    echo "== google packages =="
    adb shell pm list packages --user 0 | tr -d '\r' | sed 's/^package://' | grep -E '^(com\.google|com\.android\.vending|com\.android\.chrome)' | sort || true
    echo
    echo "== samsung packages =="
    adb shell pm list packages --user 0 | tr -d '\r' | sed 's/^package://' | grep -E '^(com\.samsung|com\.sec)' | sort || true
    echo
    echo "== recent crash signals =="
    adb logcat -d -t 3000 -v time 2>/dev/null | grep -Ei 'FATAL EXCEPTION|AndroidRuntime|ANR|Watchdog|crash' || true
  } > "$report"

  printf 'audit written to %s\n' "$report"
}

cmd_rename() {
  ensure_device
  try_setting_put global device_name
  try_setting_put system device_name
  try_setting_put secure bluetooth_name
  try_setting_put global wifi_p2p_device_name
  echo
  echo "Current values:"
  adb shell settings get global device_name | tr -d '\r' | sed 's/^/global.device_name=/'
  adb shell settings get secure bluetooth_name | tr -d '\r' | sed 's/^/secure.bluetooth_name=/'
}

cmd_install() {
  ensure_device
  if [[ ! -x "$JAVA_HOME/bin/java" ]]; then
    echo "JAVA_HOME does not point to a usable JDK: $JAVA_HOME" >&2
    exit 3
  fi

  (cd "$ROOT_DIR/HackerBoard" && JAVA_HOME="$JAVA_HOME" ./gradlew :app:assembleDebug)
  adb install -r "$ROOT_DIR/HackerBoard/app/build/outputs/apk/debug/app-debug.apk"

  (cd "$ROOT_DIR/Flipper Launcher" && JAVA_HOME="$JAVA_HOME" ./gradlew :app:assembleDebug)
  adb install -r "$ROOT_DIR/Flipper Launcher/app/build/outputs/apk/debug/app-debug.apk"

  adb shell am start -n com.a11.flipperlauncher/.MainActivity
}

cmd_set_home() {
  ensure_device
  adb shell cmd package set-home-activity com.a11.flipperlauncher/.MainActivity
  adb shell cmd package resolve-activity \
    -a android.intent.action.MAIN \
    -c android.intent.category.HOME
}

cmd_degoogle() {
  ensure_device
  load_packages

  local apply=0
  local include_optional=0
  for arg in "$@"; do
    case "$arg" in
      --apply) apply=1 ;;
      --include-optional) include_optional=1 ;;
      *) echo "unknown degoogle flag: $arg" >&2; usage; exit 1 ;;
    esac
  done

  echo "Core Google/Android packages kept:"
  printf '%s\n' $CORE_GOOGLE_KEEP | sed 's/^/  keep /'
  echo

  local targets="$SAFE_GOOGLE_DISABLE"
  if [[ "$include_optional" -eq 1 ]]; then
    targets="$targets
$OPTIONAL_GOOGLE_DISABLE"
  fi

  if [[ "$apply" -eq 0 ]]; then
    echo "Dry run. Add --apply to disable the listed installed packages."
  else
    echo "Applying reversible user-0 disables with pm disable-user."
  fi
  echo

  local pkg
  for pkg in $targets; do
    if package_installed "$pkg"; then
      if [[ "$apply" -eq 1 ]]; then
        echo "disable $pkg"
        adb shell pm disable-user --user 0 "$pkg" || true
      else
        echo "would disable $pkg"
      fi
    fi
  done
}

cmd_restore_google_apps() {
  ensure_device
  local pkg
  for pkg in $SAFE_GOOGLE_DISABLE $OPTIONAL_GOOGLE_DISABLE; do
    echo "enable $pkg"
    adb shell pm enable "$pkg" >/dev/null 2>&1 || true
  done
}

main() {
  local command="${1:-}"
  [[ -n "$command" ]] || { usage; exit 1; }
  shift || true

  case "$command" in
    audit) cmd_audit "$@" ;;
    rename) cmd_rename "$@" ;;
    install) cmd_install "$@" ;;
    set-home) cmd_set_home "$@" ;;
    degoogle) cmd_degoogle "$@" ;;
    restore-google-apps) cmd_restore_google_apps "$@" ;;
    -h|--help|help) usage ;;
    *) echo "unknown command: $command" >&2; usage; exit 1 ;;
  esac
}

main "$@"
