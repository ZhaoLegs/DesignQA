#!/bin/sh
set -eu

APK=${1:-app-debug.apk}
JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home}
export JAVA_HOME

APKTOOL_DIR=${APKTOOL_DIR:-$(mktemp -d "${TMPDIR:-/tmp}/designqa-close-test.XXXXXX")}
trap 'rm -rf "$APKTOOL_DIR"' EXIT

apktool d -f -o "$APKTOOL_DIR" "$APK" >/dev/null

SMALI=$(find "$APKTOOL_DIR" -path '*/com/legs/designqa/QaService.smali' -print -quit)
METHOD=$(sed -n \
    '/^\.method private static final buildPanel\$lambda\$20\$lambda\$19/,/^\.end method/p' \
    "$SMALI")

printf '%s\n' "$METHOD" | grep -q 'setPanelOpen(Z)V'

if printf '%s\n' "$METHOD" | grep -q 'setMode(Lcom/legs/designqa/MeasureView\$Mode;)V'; then
    echo "FAIL: closing the panel still disables the active inspection mode" >&2
    exit 1
fi

echo "PASS: closing the panel preserves the active inspection mode"
