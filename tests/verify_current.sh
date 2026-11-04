#!/bin/sh
# Current source build verification; legacy smali scripts target historical APK layouts.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}
"$ROOT/gradlew" -p "$ROOT" :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
python3 "$ROOT/tests/test_overlay_state.py"
python3 "$ROOT/tests/test_screenshot_gate.py"
python3 "$ROOT/tests/test_service_lifecycle.py"
python3 "$ROOT/tests/test_connection_status.py"
python3 "$ROOT/tests/test_feature_integration.py"
python3 "$ROOT/tests/test_capture_races.py"
python3 "$ROOT/tests/test_switch_preferences.py"
python3 "$ROOT/tests/test_style_regressions.py"
