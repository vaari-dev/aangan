#!/bin/bash
# Starts Aangan on a running Android TV emulator or TV and fails if it crashes or stops responding:
# the welcome, the home screen, Settings and the phone page, the dock's menu, a few festival scenes,
# the screensaver, and the whole thing in Hindi.
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

# First start: the home screen, then the welcome on top of it.
adb shell am start -W -n "$HOME_ACTIVITY"
shot 1-welcome 5
adb shell input keyevent KEYCODE_BACK
shot 2-home

# Settings, and the phone page it serves. Until Aangan is the home screen, its setup tile leads the
# grid, just below the dock where focus starts.
adb shell input keyevent KEYCODE_DPAD_DOWN
adb shell input keyevent KEYCODE_DPAD_CENTER
shot 2-settings 4
adb shell uiautomator dump /sdcard/ui.xml > /dev/null
# The link under the QR code: "10.0.2.15:8737/secret".
LINK=$(adb shell cat /sdcard/ui.xml | grep -oE 'text="[0-9.]+:[0-9]+/[^"]+"' | head -1 | sed 's/^text="//; s/"$//')
if [ -z "$LINK" ]; then
    echo "Settings didn't show the phone page's address." >&2
    adb shell cat /sdcard/ui.xml >&2
    exit 1
fi
HOST="${LINK%%/*}"
# Asks the TV's server from the TV's own shell, as a phone on the network would.
ask() {
    adb shell "printf '$1 /${LINK#*/}$2 HTTP/1.1\r\nHost: $HOST\r\nContent-Length: ${#3}\r\nContent-Type: application/x-www-form-urlencoded\r\nConnection: close\r\n\r\n$3' | nc -w 10 ${HOST%:*} ${HOST#*:}"
}
expect() {
    if ! grep -q "$1" <<< "$2"; then
        echo "The phone page didn't answer with \"$1\":" >&2
        echo "$2" | head -40 >&2
        exit 1
    fi
}
if adb shell command -v nc > /dev/null; then
    expect "HTTP/1.1 200" "$(ask GET "" "")"
    expect "HTTP/1.1 303" "$(ask POST /add "who=Asha&month=1&day=9&kind=birthday")"
    expect '"who": *"Asha"' "$(ask GET /export "")"
    shot 2-settings-changed 2
else
    echo "This TV has no nc, so the phone page itself isn't checked."
fi
adb shell input keyevent KEYCODE_BACK

adb shell cmd package set-home-activity "$HOME_ACTIVITY"

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
# "Does not have a focused window" is the emulator being slow to show a window, and Google's own apps
# get it too; Aangan's main thread being stuck reads "Waited 5000ms for KeyEvent" or similar.
if adb logcat -d -b events | grep "am_anr" | grep "$PKG" | grep -vq "does not have a focused window"; then
    echo "Aangan stopped responding:" >&2
    adb logcat -d -b events | grep "am_anr" >&2
    exit 1
fi
echo "Aangan ran without crashing or stopping responding."
