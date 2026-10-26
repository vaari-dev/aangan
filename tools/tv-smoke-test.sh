#!/bin/bash
# Drives Aangan on an emulator and fails if a step doesn't show Aangan, or it crashes or hangs.
# Not for a real TV: it replaces the accessibility services and screensaver, and adds a birthday.
#
#   tools/tv-smoke-test.sh path/to/aangan.apk [screenshot-dir]
#
# Needs adb, and zbarimg (zbar-tools) to read the QR code in Settings.
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

front() {
    adb shell dumpsys activity activities | grep -m1 -E "topResumedActivity|mResumedActivity" || true
}

# Fails unless the screen in front is Aangan's, or the given activity of it.
expect_front() {
    if ! front | grep -q "$PKG/${2:-}"; then
        echo "$1: expected Aangan in front, got: $(front)" >&2
        exit 1
    fi
}

adb wait-for-device
adb logcat -c
adb install -r "$APK"

adb shell am start -W -n "$HOME_ACTIVITY"
shot 1-welcome 5
expect_front welcome .WelcomeActivity
adb shell input keyevent KEYCODE_BACK
shot 2-home
expect_front home .HomeActivity

# Until Aangan is set up, its tile leads the grid, below the dock where focus starts.
adb shell input keyevent KEYCODE_DPAD_DOWN
adb shell input keyevent KEYCODE_DPAD_CENTER
shot 2-settings 4
expect_front settings .SetupActivity

# The phone page, at the address in the QR code, asked from the TV's own shell as a phone would.
LINK=$(zbarimg -q --raw "$SHOTS/2-settings.png" || true)
LINK="${LINK#http://}"
if [ -z "$LINK" ]; then
    echo "No readable QR code in $SHOTS/2-settings.png." >&2
    exit 1
fi
HOST="${LINK%%/*}"
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
expect "HTTP/1.1 200" "$(ask GET "" "")"
expect "HTTP/1.1 303" "$(ask POST /add "who=Asha&month=1&day=9&kind=birthday")"
expect '"who": *"Asha"' "$(ask GET /export "")"
shot 2-settings-changed 2

# City is the first row.
adb shell input keyevent KEYCODE_DPAD_CENTER
shot 2-city
expect_front city .CityActivity
adb shell input keyevent KEYCODE_BACK
adb shell input keyevent KEYCODE_BACK

# Home through Aangan's accessibility service, as people set it up on TVs that can't change home app.
adb shell settings put secure enabled_accessibility_services "$PKG/.HomeButton"
adb shell settings put secure accessibility_enabled 1
sleep 3
adb shell input keyevent KEYCODE_HOME
shot 3-home-button
expect_front "Home button" .HomeActivity

# Names go through adb's remote shell, so none with an apostrophe.
for festival in Diwali Holi "Karwa Chauth" Christmas Lohri; do
    adb shell am start -W -n "$HOME_ACTIVITY" --es festival "'$festival'"
    shot "4-$(echo "$festival" | tr -cd 'A-Za-z')" 4
done

adb shell input keyevent KEYCODE_HOME
# Keys sent before Aangan's window is back are dropped.
sleep 2
adb shell input keyevent KEYCODE_DPAD_UP
adb shell input keyevent KEYCODE_DPAD_CENTER
shot 5-today
expect_front today .HomeActivity
adb shell input keyevent KEYCODE_BACK

adb shell input keyevent KEYCODE_DPAD_DOWN
adb shell input keyevent --longpress KEYCODE_DPAD_CENTER
shot 6-app-menu 2
expect_front "app menu" .HomeActivity
adb shell input keyevent KEYCODE_BACK

# Google TV offers only its own Ambient mode, so this runs where the TV lets a shell start a dream.
adb shell settings put secure screensaver_components "$PKG/.ClockDream"
adb shell settings put secure screensaver_enabled 1
if adb shell cmd dreams start-dreaming > /dev/null 2>&1; then
    shot 7-screensaver 5
    if ! adb shell dumpsys dreams | grep -q "ClockDream"; then
        echo "The screensaver didn't start:" >&2
        adb shell dumpsys dreams >&2
        exit 1
    fi
    adb shell cmd dreams stop-dreaming > /dev/null 2>&1 || true
else
    echo "This TV can't start a screensaver from the shell; skipping it."
fi

adb shell am force-stop "$PKG"
adb shell cmd locale set-app-locales "$PKG" --locales hi
adb shell am start -W -n "$HOME_ACTIVITY"
shot 8-hindi 5
expect_front hindi .HomeActivity
adb shell cmd locale set-app-locales "$PKG" --locales ""

CRASHES=$(adb logcat -d -b crash)
if grep -q "$PKG" <<< "$CRASHES"; then
    echo "Aangan crashed:" >&2
    echo "$CRASHES" >&2
    exit 1
fi
# "Does not have a focused window" is the emulator being slow to show a window; Google's apps get it too.
ANRS=$(adb logcat -d -b events | grep "am_anr" || true)
if grep "$PKG" <<< "$ANRS" | grep -v "does not have a focused window" > /dev/null; then
    echo "Aangan stopped responding:" >&2
    echo "$ANRS" >&2
    exit 1
fi
echo "Aangan passed."
