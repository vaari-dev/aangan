#!/bin/bash
# Starts Aangan on a running Android TV emulator or TV and fails if it crashes or stops responding:
# the welcome, the home screen and the dock's menu, a few festival scenes, the screensaver, and the
# whole thing in Hindi.
# CI runs it on the minified release build, which is where shrinking mistakes would show.
#
#   tools/tv-smoke-test.sh path/to/aangan.apk [screenshot-dir]
set -euo pipefail
APK="$1"
SHOTS="${2:-screenshots}"
PKG=dev.vaari.aangan
HOME_ACTIVITY="$PKG/.HomeActivity"
mkdir -p "$SHOTS"

shot() {
    sleep "${2:-3}"
    adb exec-out screencap -p > "$SHOTS/$1.png"
}

adb wait-for-device
adb logcat -c
adb install -r "$APK"
adb shell cmd package set-home-activity "$HOME_ACTIVITY"

# First start: the home screen, then the welcome on top of it.
adb shell am start -W -n "$HOME_ACTIVITY"
shot 1-welcome 5
adb shell input keyevent KEYCODE_BACK
shot 2-home

# Festival scenes, as the developer preview shows them (see HomeActivity.onNewIntent).
# (Names are passed through adb's remote shell, so none with an apostrophe.)
for festival in Diwali Holi "Karwa Chauth" Christmas Lohri; do
    adb shell am start -W -n "$HOME_ACTIVITY" --es festival "'$festival'"
    shot "3-$(echo "$festival" | tr -cd 'A-Za-z')" 4
done

# The Home button returns to Aangan, and the Today card opens with Up then OK.
adb shell input keyevent KEYCODE_HOME
adb shell input keyevent KEYCODE_DPAD_UP
adb shell input keyevent KEYCODE_DPAD_CENTER
shot 4-today
adb shell input keyevent KEYCODE_BACK

# Holding OK on an app opens its menu (put it in the dock, hide it); Back closes it.
adb shell input keyevent KEYCODE_DPAD_DOWN
adb shell input keyevent --longpress KEYCODE_DPAD_CENTER
shot 5-app-menu 2
adb shell input keyevent KEYCODE_BACK

# The screensaver, where the TV lets a shell start one (Android 12 and later have "cmd dreams").
if adb shell cmd dreams start-dreaming "$PKG/.ClockDream" 0 > /dev/null 2>&1; then
    shot 6-screensaver 5
    adb shell cmd dreams stop-dreaming > /dev/null 2>&1 || true
else
    echo "This TV can't start the screensaver from the shell; skipping it."
fi

# In Hindi: Android 13 and later can set one app's language from the shell.
adb shell am force-stop "$PKG"
adb shell cmd locale set-app-locales "$PKG" --locales hi
adb shell am start -W -n "$HOME_ACTIVITY"
shot 7-hindi 5
adb shell cmd locale set-app-locales "$PKG" --locales ""

if ! adb shell pidof "$PKG" > /dev/null; then
    echo "Aangan isn't running at the end of the test." >&2
    adb logcat -d | tail -200 >&2
    exit 1
fi
if adb logcat -d -b crash | grep -q "$PKG"; then
    echo "Aangan crashed:" >&2
    adb logcat -d -b crash >&2
    exit 1
fi
if adb logcat -d -b events | grep "am_anr" | grep -q "$PKG"; then
    echo "Aangan stopped responding:" >&2
    adb logcat -d -b events | grep "am_anr" >&2
    exit 1
fi
echo "Aangan ran without crashing or stopping responding."
