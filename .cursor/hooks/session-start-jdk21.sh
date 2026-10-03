#!/bin/sh
# Pin JDK 21 for this Cursor session (hooks + agent shells that inherit env).
set -eu

ROOT=$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)
# shellcheck disable=SC1091
. "$ROOT/scripts/jdk21.sh"

if ! home=$(jdk21_resolve); then
  printf '%s\n' '{
  "additional_context": "JDK 21 was not found. Install Homebrew openjdk@21 (or set WORKTIME_JAVA_HOME) before building."
}'
  exit 0
fi

path="$home/bin:${PATH:-}"
python3 -c '
import json, os, sys
home, path = sys.argv[1], sys.argv[2]
print(json.dumps({
    "env": {
        "JAVA_HOME": home,
        "PATH": path,
        "WORKTIME_JAVA_HOME": home,
    },
    "additional_context": (
        "This project requires JDK 21 for Gradle/Kotlin. "
        f"JAVA_HOME is set to {home}. Always build with ./gradlew."
    ),
}))
' "$home" "$path"
