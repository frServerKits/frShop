#!/usr/bin/env bash
#
# Build LimitShop against a Paper version, resolving the newest stable build
# automatically from PaperMC's fill API.
#
# Usage:
#   ./scripts/build-paper.sh                 # build the default version from pom.xml
#   ./scripts/build-paper.sh 26.2            # newest stable Paper 26.2 build
#   ./scripts/build-paper.sh 26.3            # newest stable Paper 26.3 build
#   ./scripts/build-paper.sh latest          # newest Paper release
#   ./scripts/build-paper.sh 26.2 -DskipTests
#
# The script only overrides Maven properties, so it never edits the project.
# The output jar is named LimitShop-<project version>-paper<api version>.jar.
#
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

usage() {
    sed -n '2,15p' "$0" | sed 's/^# \{0,1\}//'
}

for tool in curl python3 mvn; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        echo "error: '$tool' is required but was not found in PATH" >&2
        exit 1
    fi
done

DEFAULT_VERSION="$(sed -n 's:.*<paper.api.version>\([^<]*\)</paper.api.version>.*:\1:p' pom.xml | head -n1)"
TARGET="${1:-$DEFAULT_VERSION}"
shift || true

case "$TARGET" in
    -h|--help|help)
        usage
        exit 0
        ;;
esac

if [[ "$TARGET" == "latest" ]]; then
    TARGET="$(
        curl -fsSL "https://fill.papermc.io/v3/projects/paper" |
            python3 -c 'import json, sys; d = json.load(sys.stdin); print(next(iter(d["versions"])))'
    )"
    echo "Latest Paper release: $TARGET"
fi

TARGET="${TARGET#v}"
TARGET="${TARGET#paper-}"

# Accept both a plain version ("26.2") and a Maven artifact version
# ("26.2.build.134-stable").
if [[ "$TARGET" =~ ^(.+)\.build\.([0-9]+)-(stable|beta|alpha)$ ]]; then
    VERSION="${BASH_REMATCH[1]}"
    BUILD="${BASH_REMATCH[2]}"
    CHANNEL="${BASH_REMATCH[3]}"
else
    VERSION="$TARGET"
    BUILDS_JSON="$(curl -fsSL "https://fill.papermc.io/v3/projects/paper/versions/${VERSION}/builds")"
    read -r BUILD CHANNEL < <(
        printf '%s' "$BUILDS_JSON" |
            python3 -c '
import json, sys
builds = json.load(sys.stdin)
stable = [b for b in builds if b.get("channel") == "STABLE"]
if not stable:
    sys.exit("no stable build found for this Paper version")
best = max(stable, key=lambda b: b["id"])
print(best["id"], best["channel"].lower())
'
    )
fi

ARTIFACT="${VERSION}.build.${BUILD}-${CHANNEL}"
API_VERSION="$(
    python3 -c 'import sys; p = sys.argv[1].split("."); print(".".join(p[:2]))' "$VERSION"
)"
PROJECT_VERSION="$(
    python3 -c 'import xml.etree.ElementTree as ET; ns = {"m": "http://maven.apache.org/POM/4.0.0"}; print(ET.parse("pom.xml").getroot().findtext("m:version", namespaces=ns))'
)"

echo "Paper version : $VERSION"
echo "Paper API     : $ARTIFACT"
echo "plugin.yml    : api-version '$API_VERSION'"
echo

mvn -B clean package \
    "-Dpaper.version=$ARTIFACT" \
    "-Dpaper.api.version=$API_VERSION" \
    "$@"

JAR="target/LimitShop-${PROJECT_VERSION}-paper${API_VERSION}.jar"
if [[ -f "$JAR" ]]; then
    echo
    echo "Built: $ROOT/$JAR"
else
    echo "warning: expected $JAR was not produced" >&2
    exit 1
fi
