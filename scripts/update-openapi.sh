#!/usr/bin/env sh
# Vendor petkit-local's /app/v1 contract at a given git ref (tag, branch or commit).
#
#   scripts/update-openapi.sh <git-ref>
#
# Writes api/openapi.yaml and records the ref in api/SOURCE. The contract tests in
# core:network read the vendored copy, so a server change breaks a test, not the app.
set -eu

if [ $# -ne 1 ] || [ -z "$1" ]; then
  echo "usage: $0 <git-ref>" >&2
  exit 2
fi

ref="$1"
root="$(cd "$(dirname "$0")/.." && pwd)"
url="https://raw.githubusercontent.com/Gamso/petkit-local/${ref}/addon/petkit_local/app_api/openapi.yaml"
tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT

curl --fail --silent --show-error --location --retry 3 "$url" -o "$tmp"

# A 200 with an HTML error page or an empty body must not replace a good contract.
if ! grep -q '^openapi: ' "$tmp"; then
  echo "error: $url is not an OpenAPI document" >&2
  exit 1
fi

mkdir -p "$root/api"
mv "$tmp" "$root/api/openapi.yaml"
trap - EXIT
printf '%s\n' "$ref" > "$root/api/SOURCE"
echo "api/openapi.yaml <- petkit-local@${ref}"
