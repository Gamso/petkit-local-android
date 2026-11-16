#!/usr/bin/env bash
# Runs the Gradle wrapper and, when the build failed because a download failed, runs it again.
#
#   scripts/gradle-ci.sh <gradle arguments...>
#
# CI runners share their addresses with many others, and Maven Central, Google's Maven and the
# Gradle Plugin Portal sometimes answer 429 or 5xx, or nothing, for a minute. The build then dies
# while resolving a plugin or a dependency, before anything was compiled or tested, and a re-run
# passes. Only that is retried: a failed test, a lint finding or a compile error does not match
# and fails at once, as before.
#
# Environment:
#   GRADLE_CI_ATTEMPTS  how many times to run Gradle in all (default 3)
#   GRADLE_CI_PAUSE     seconds to wait before the first retry, doubled each time (default 30)
#   GRADLE_CI_WRAPPER   the wrapper to run (default ./gradlew), e.g. mock-server's own
set -uo pipefail

attempts=${GRADLE_CI_ATTEMPTS:-3}
pause=${GRADLE_CI_PAUSE:-30}
wrapper=${GRADLE_CI_WRAPPER:-./gradlew}
log=$(mktemp)
trap 'rm -f "$log"' EXIT

# What Gradle prints when a download, not the code, broke the build.
download_failure='was not found in any of the following sources|Could not resolve all (files|artifacts|dependencies)|Could not resolve [^ ]+:[^ ]+:|Could not (GET|HEAD) |Received status code (429|5[0-9][0-9])|Read timed out|Connect timed out|Connection reset|Remote host terminated the handshake|Could not download .*\.(jar|pom|module|zip)'

for attempt in $(seq 1 "$attempts"); do
    "$wrapper" "$@" 2>&1 | tee "$log"
    status=${PIPESTATUS[0]}
    if [ "$status" -eq 0 ]; then
        exit 0
    fi
    if ! grep -Eq "$download_failure" "$log"; then
        exit "$status"
    fi
    if [ "$attempt" -lt "$attempts" ]; then
        echo "::warning::Gradle failed while downloading (attempt $attempt of $attempts); retrying in ${pause}s."
        sleep "$pause"
        pause=$((pause * 2))
    fi
done
echo "::error::Gradle failed while downloading, $attempts times in a row."
exit "$status"
