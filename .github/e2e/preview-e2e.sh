#!/bin/bash
# End-to-end check of "See how it looks", driven over adb on a TV emulator.
set -euo pipefail
APK="$1"; SHOTS="$2"; HERE="$(dirname "$0")"
PKG=dev.vaari.aangan
mkdir -p "$SHOTS"
n=0
shot() { n=$((n+1)); adb exec-out screencap -p > "$SHOTS/$(printf %02d $n)-$1.png"; }
dump() {
    for _ in 1 2 3 4 5; do
        if adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1; then adb exec-out cat /sdcard/ui.xml; return; fi
        sleep 1
    done
    echo "uiautomator dump failed" >&2; exit 1
}
focused() { dump | python3 "$HERE/ui.py" focused; }
has() { dump | python3 "$HERE/ui.py" has "$1"; }
key() { adb shell input keyevent "$@"; sleep 1; }
fail() { echo "FAIL: $*" >&2; shot fail; exit 1; }
pass() { echo "PASS: $*"; }
front() { adb shell dumpsys activity activities | grep -m1 -E "topResumedActivity|mResumedActivity" || true; }
expect_front() { front | grep -q "$PKG/$1" || fail "$2: expected $1 in front, got $(front)"; }
expect() { has "$1" || fail "$2: no \"$1\" on screen"; }
expect_not() { if has "$1"; then fail "$2: \"$1\" still on screen"; fi; }
# Moves focus with $2 (default down) until the focused row's first text is exactly $1.
focus_on() {
    local dir="${2:-KEYCODE_DPAD_DOWN}" last="" same=0 now
    for _ in $(seq 1 80); do
        now="$(focused)"
        [ "$now" = "$1" ] && return
        if [ "$now" = "$last" ]; then same=$((same+1)); [ $same -ge 3 ] && fail "couldn't focus \"$1\" (stuck on \"$now\")"; else same=0; fi
        last="$now"
        key "$dir"
    done
    fail "couldn't focus \"$1\""
}
open_settings() {
    adb shell am start -W -n "$PKG/.HomeActivity" > /dev/null; sleep 2
    # Until Aangan is set up, its tile leads the grid, below the dock.
    key KEYCODE_DPAD_DOWN; key KEYCODE_DPAD_CENTER; sleep 2
    expect_front .SetupActivity "open settings"
}
preview_from_settings() {  # $1 row title, $2 expected festival name in the note
    focus_on "$1"
    key KEYCODE_DPAD_CENTER
    expect "See how it looks" "$1 menu"
    focus_on "See how it looks"
    key KEYCODE_DPAD_CENTER; sleep 3
    expect_front .HomeActivity "$1 preview"
    expect "A preview of $2" "$1 preview note"
}

adb wait-for-device
adb logcat -c
adb install -r "$APK"
adb shell am start -W -n "$PKG/.HomeActivity"; sleep 5
key KEYCODE_BACK; sleep 2   # past the welcome
expect_front .HomeActivity home
adb shell settings put secure enabled_accessibility_services "$PKG/.HomeButton"
adb shell settings put secure accessibility_enabled 1
sleep 2

# 1. Settings -> Diwali -> See how it looks.
open_settings
shot settings
preview_from_settings Diwali Diwali
expect "Happy Diwali" "Diwali greeting"
sleep 3; shot diwali-preview
pass "Diwali preview from Settings shows its scene, greeting and note"

# 2. Back returns to Settings on the Diwali row.
key KEYCODE_BACK; sleep 2
expect_front .SetupActivity "Back from preview"
[ "$(focused)" = "Diwali" ] || fail "Back from preview: focus on \"$(focused)\", not Diwali"
shot back-to-settings
pass "Back returns to Settings with Diwali focused"

# 3. Back again: an ordinary home screen.
key KEYCODE_BACK; sleep 2
expect_front .HomeActivity "home after preview"
expect_not "A preview of" "home after preview"
expect_not "Happy Diwali" "home after preview"
shot home-after
pass "Leaving the preview leaves no festival behind"

# 4. Home button ends a preview.
open_settings
preview_from_settings Diwali Diwali
key KEYCODE_HOME; sleep 3
expect_front .HomeActivity "Home during preview"
expect_not "A preview of" "Home during preview"
expect_not "Happy Diwali" "Home during preview"
pass "Home ends a preview"

# 5. Opening another app ends a preview; Back from it comes home without it.
adb shell am start -W -n "$PKG/.HomeActivity" --es festival Holi > /dev/null; sleep 3
expect "A preview of Holi" "adb preview"
adb shell am start -W -a android.settings.SETTINGS > /dev/null; sleep 3
key KEYCODE_BACK; sleep 3
expect_front .HomeActivity "back from another app"
expect_not "A preview of" "back from another app"
expect_not "Happy Holi" "back from another app"
pass "adb preview works, and leaving for an app ends it"

# 6. A greeting-only festival offers no preview.
open_settings
focus_on "Haryana Day"
key KEYCODE_DPAD_CENTER
expect "Change the date" "Haryana Day menu"
expect_not "See how it looks" "Haryana Day menu"
shot plain-menu
key KEYCODE_BACK
pass "A greeting-only festival has no See how it looks"

# 7. A hidden festival can still be previewed.
focus_on Diwali
key KEYCODE_DPAD_CENTER
focus_on "Hide it"
key KEYCODE_DPAD_CENTER; sleep 2
[ "$(focused)" = "Diwali" ] || fail "after hiding, focus on \"$(focused)\""
expect "Hidden" "Diwali hidden"
preview_from_settings Diwali Diwali
shot hidden-preview
key KEYCODE_BACK; sleep 2
expect_front .SetupActivity "Back from hidden preview"
key KEYCODE_DPAD_CENTER
focus_on "Show it"
key KEYCODE_DPAD_CENTER; sleep 1
pass "A hidden festival can be previewed"

# 8. A family day, added from the phone page.
LINK=$(zbarimg -q --raw "$SHOTS/01-settings.png" || true); LINK="${LINK#http://}"
[ -n "$LINK" ] || fail "no QR code"
HOST="${LINK%%/*}"; BODY="who=Asha&month=1&day=9&kind=birthday"
adb shell "printf 'POST /${LINK#*/}/add HTTP/1.1\r\nHost: $HOST\r\nContent-Length: ${#BODY}\r\nContent-Type: application/x-www-form-urlencoded\r\nConnection: close\r\n\r\n$BODY' | nc -w 10 ${HOST%:*} ${HOST#*:}" | head -1
sleep 2
focus_on Asha KEYCODE_DPAD_UP
preview_from_settings Asha "Asha's birthday"
expect "Happy birthday, Asha" "birthday greeting"
sleep 3; shot birthday-preview
key KEYCODE_BACK; sleep 2
expect_front .SetupActivity "Back from birthday preview"
[ "$(focused)" = "Asha" ] || fail "Back from birthday preview: focus on \"$(focused)\""
pass "A family day previews with its greeting, and Back returns to its row"

if adb logcat -d | grep -E "FATAL EXCEPTION|ANR in $PKG"; then fail "crash or ANR in logcat"; fi
pass "no crash or ANR"
