#!/bin/sh
# After each agent turn: build + unit tests. On failure, ask the agent to fix.
set -eu

ROOT=$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)
cd "$ROOT"

# shellcheck disable=SC1091
. "$ROOT/scripts/jdk21.sh"

INPUT=$(cat || true)
STATUS=$(printf '%s' "$INPUT" | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d.get("status",""))' 2>/dev/null || true)
LOOP_COUNT=$(printf '%s' "$INPUT" | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d.get("loop_count",0))' 2>/dev/null || echo 0)

emit_empty() {
  printf '%s\n' '{}'
  exit 0
}

# Only verify clean completions.
if [ "$STATUS" != "completed" ]; then
  emit_empty
fi

# Escape hatch for this turn.
if [ -f "$ROOT/.cursor/skip-build-on-stop" ]; then
  rm -f "$ROOT/.cursor/skip-build-on-stop"
  emit_empty
fi

if ! jdk21_export; then
  python3 -c '
import json
print(json.dumps({
  "followup_message": (
    "Stop hook: JDK 21 is required but was not found.\n\n"
    "Install it (e.g. `brew install openjdk@21`) or set WORKTIME_JAVA_HOME "
    "to a JDK 21 home, then rebuild with `./gradlew :app:assembleDebug "
    ":app:testDebugUnitTest`."
  )
}))
'
  exit 0
fi

LOG=$(mktemp)
trap 'rm -f "$LOG"' EXIT

set +e
./gradlew :app:assembleDebug :app:testDebugUnitTest --console=plain >"$LOG" 2>&1
CODE=$?
set -e

if [ "$CODE" -eq 0 ]; then
  emit_empty
fi

python3 -c '
import json, sys
loop_count = sys.argv[1]
code = int(sys.argv[2])
log_path = sys.argv[3]
with open(log_path, "r", errors="replace") as f:
    out = f.read()[-12000:]
msg = (
    "Stop hook: the project build/tests failed after your last turn "
    f"(loop {loop_count}).\n\n"
    "JAVA_HOME is pinned to JDK 21 via scripts/jdk21.sh.\n"
    "Command: `./gradlew :app:assembleDebug :app:testDebugUnitTest`\n"
    f"Exit code: {code}\n\n"
    "Fix the failures below, then stop. Do not skip verification.\n"
    "If you must skip one turn, create `.cursor/skip-build-on-stop`.\n\n"
    "```text\n"
    f"{out}\n"
    "```\n"
)
print(json.dumps({"followup_message": msg}, ensure_ascii=False))
' "$LOOP_COUNT" "$CODE" "$LOG"
exit 0
