#!/usr/bin/env bash
# Starts the real petkit-local server for the end-to-end tests, seeded and ready to sign in to.
#
#   scripts/real-server.sh up      # start it, seed it, set the app password
#   scripts/real-server.sh logs    # its log
#   scripts/real-server.sh down    # stop it and delete its data
#
# The image is e2e/docker-compose.yml's service petkit-local: the floating `dev` tag, pulled
# afresh. `up`:
#
# 1. starts it without Home Assistant, waits until its panel answers, and says which build it
#    got: the image's digest and the petkit-local commit it was built from (its
#    org.opencontainers.image.revision label), also in the GitHub job summary when run there;
# 2. seeds it with the server's own smoke test (addon/tests/smoke/smoke.py), taken at that same
#    commit so script and server cannot drift apart (at main, saying so, if the image has no
#    label). It registers a fake T5 litter box over HTTP, connects it over MQTT and posts its
#    telemetry, then disconnects. It runs in a second container of the same image, in the
#    server's network, as the server's run.sh runs it;
# 3. sets the app API's password from the panel (Setup -> Phone app access).
#
# Then it prints the instrumentation arguments that point the tests at it (core:e2e TestServer).
#
# Environment:
#   PETKIT_SERVER_PORT      the host port of the app API (default 8297)
#   PETKIT_PANEL_PORT       the host port of the panel, on 127.0.0.1 only (default 8299)
#   PETKIT_SERVER_PASSWORD  the app password to set (default petkit-e2e-password)
#   PETKIT_SMOKE_SCRIPT     a smoke.py to run instead of downloading the image's own
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
compose=(docker compose -f "$root/e2e/docker-compose.yml" --profile server)
service=petkit-local
repository=https://github.com/Gamso/petkit-local
server_port=${PETKIT_SERVER_PORT:-8297}
panel_port=${PETKIT_PANEL_PORT:-8299}
password=${PETKIT_SERVER_PASSWORD:-petkit-e2e-password}
export PETKIT_SERVER_PORT=$server_port PETKIT_PANEL_PORT=$panel_port

up() {
    "${compose[@]}" up --detach --wait "$service"
    local container image digest revision version source smoke
    container=$("${compose[@]}" ps --quiet "$service")
    image=$(docker inspect --format '{{.Image}}' "$container")
    digest=$(docker inspect --format '{{range .RepoDigests}}{{println .}}{{end}}' "$image" | head -n 1)
    revision=$(docker inspect --format '{{index .Config.Labels "org.opencontainers.image.revision"}}' "$image")
    version=$(panel GET /api/info | sed -n 's/.*"version": *"\([^"]*\)".*/\1/p')

    if [ -n "${PETKIT_SMOKE_SCRIPT:-}" ]; then
        smoke=$PETKIT_SMOKE_SCRIPT
        source="$smoke (PETKIT_SMOKE_SCRIPT)"
    else
        if [ -n "$revision" ]; then
            source="$repository/blob/$revision/addon/tests/smoke/smoke.py"
        else
            echo "::warning::The image has no org.opencontainers.image.revision label: seeding it with main's smoke test, which may not match it."
            source="$repository/blob/main/addon/tests/smoke/smoke.py (no revision label: main)"
        fi
        smoke=$(mktemp)
        # shellcheck disable=SC2064 # the path now: `smoke` is local to this function.
        trap "rm -f '$smoke'" EXIT
        curl --fail --silent --show-error --location --retry 3 \
            "https://raw.githubusercontent.com/Gamso/petkit-local/${revision:-main}/addon/tests/smoke/smoke.py" -o "$smoke"
    fi

    local commit="unknown (no revision label)" report
    if [ -n "$revision" ]; then commit="[\`$revision\`]($repository/commit/$revision)"; fi
    report=$(
        echo "### petkit-local under test"
        echo
        echo "| | |"
        echo "|---|---|"
        echo "| Image | \`${digest:-$image}\` |"
        echo "| Commit | $commit |"
        echo "| Version | ${version:-unknown} |"
        echo "| Seeded by | $source |"
    )
    echo "$report"
    if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then echo "$report" >> "$GITHUB_STEP_SUMMARY"; fi

    echo "Seeding it with the server's smoke test:"
    docker run --rm -i --network "container:$container" --entrypoint python3 "$image" - < "$smoke"

    panel POST /api/app-access/password "{\"password\": \"$password\"}" > /dev/null
    echo "App password set. Run the tests against it with:"
    echo "  -Pandroid.testInstrumentationRunnerArguments.petkitServerKind=real"
    echo "  -Pandroid.testInstrumentationRunnerArguments.petkitServerUrl=http://10.0.2.2:$server_port"
    echo "  -Pandroid.testInstrumentationRunnerArguments.petkitServerPanelUrl=http://10.0.2.2:$panel_port"
    echo "  -Pandroid.testInstrumentationRunnerArguments.petkitServerPassword=$password"
}

# A request to the panel, from the host.
panel() {
    local method=$1 path=$2 data=()
    if [ $# -ge 3 ]; then data=(--header 'Content-Type: application/json' --data "$3"); fi
    curl --fail --silent --show-error --request "$method" "${data[@]}" "http://127.0.0.1:$panel_port$path"
}

case "${1:-}" in
    up) up ;;
    logs) "${compose[@]}" logs --no-color "$service" ;;
    down) "${compose[@]}" rm --force --stop --volumes "$service" ;;
    *)
        echo "usage: $0 up|logs|down" >&2
        exit 2
        ;;
esac
