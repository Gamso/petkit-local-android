#!/usr/bin/env bash
# Smoke test of an APK, normally the release one (R8), on the emulator or device adb is connected
# to (docs/ARCHITECTURE.md §6): it installs, starts, reaches its first screen and is still running
# a few seconds later, with no crash and no ANR in the log.
#
#   scripts/smoke-test.sh app/build/outputs/apk/release/app-release.apk [output dir]
#
# The output dir (default build/smoke) receives the evidence: `am start` output, logcat, the
# crash buffer, a screenshot and the UI hierarchy. CI uploads it when the test fails.
#
# Environment:
#   SMOKE_EXPECT_TEXT     text the first screen must show, an extended regex (default
#                         "Petkit Local": a fresh install is signed out, so the first screen is
#                         sign-in, whose heading is the app's name, the same in both languages)
#   SMOKE_SETTLE_SECONDS  how long the app runs before it is checked (default 10)
#   ANDROID_SERIAL        which device, when adb sees several
set -euo pipefail
# Git Bash on Windows would rewrite the device paths below (/sdcard/...) into Windows paths.
export MSYS_NO_PATHCONV=1

apk=${1:?usage: smoke-test.sh <apk> [output dir]}
out=${2:-build/smoke}
expect=${SMOKE_EXPECT_TEXT:-Petkit Local}
settle=${SMOKE_SETTLE_SECONDS:-10}
package=org.petkitlocal.android
activity=$package/.MainActivity

mkdir -p "$out"
failures=0
fail() { echo "FAIL: $*" >&2; failures=$((failures + 1)); }
pass() { echo "ok: $*"; }

adb wait-for-device
# A debug build of the app is signed with another key and would refuse the update.
adb uninstall "$package" >/dev/null 2>&1 || true
adb install "$apk" >/dev/null
pass "installed $(basename "$apk")"

adb logcat -c
adb logcat -b crash -c 2>/dev/null || true

# -W waits until the activity has drawn its first frame and reports the launch.
adb shell am start -W -n "$activity" | tr -d '\r' | tee "$out/am-start.txt" || true
if grep -q '^Status: ok' "$out/am-start.txt"; then
    pass "started $activity"
else
    fail "am start did not report Status: ok"
fi

sleep "$settle"

adb logcat -d > "$out/logcat.txt"
adb logcat -d -b crash > "$out/crash.txt" 2>/dev/null || true
adb exec-out screencap -p > "$out/screen.png" || true
adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 &&
    adb pull /sdcard/window.xml "$out/window.xml" >/dev/null 2>&1 || true

if grep -q "FATAL EXCEPTION" "$out/logcat.txt" "$out/crash.txt"; then
    fail "the app crashed (FATAL EXCEPTION in $out/logcat.txt)"
else
    pass "no crash in the log"
fi
if grep -q "ANR in $package" "$out/logcat.txt"; then
    fail "the app stopped responding (ANR in $out/logcat.txt)"
else
    pass "no ANR in the log"
fi
if adb shell pidof "$package" >/dev/null; then
    pass "the process is alive after ${settle}s"
else
    fail "the process is gone after ${settle}s"
fi
if adb shell dumpsys activity activities | tr -d '\r' | grep -E 'ResumedActivity' | grep -q "$activity"; then
    pass "$activity is the resumed activity"
else
    fail "$activity is not the resumed activity"
fi
if [ -f "$out/window.xml" ] && grep -Eq "text=\"($expect)\"" "$out/window.xml"; then
    pass "the first screen shows \"$expect\""
else
    fail "the first screen does not show \"$expect\" (see $out/window.xml and $out/screen.png)"
fi

if [ "$failures" -gt 0 ]; then
    echo "Smoke test failed: $failures check(s). Evidence in $out/" >&2
    exit 1
fi
echo "Smoke test passed."
