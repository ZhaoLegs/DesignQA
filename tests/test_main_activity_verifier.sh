#!/bin/sh
set -eu

APK=${1:-app-debug.apk}
APKTOOL_DIR=${APKTOOL_DIR:-$(mktemp -d "${TMPDIR:-/tmp}/designqa-main-verifier-test.XXXXXX")}
trap 'rm -rf "$APKTOOL_DIR"' EXIT

apktool d -f -o "$APKTOOL_DIR" "$APK" >/dev/null

MAIN=$(find "$APKTOOL_DIR" -path '*/com/legs/designqa/MainActivity.smali' -print -quit)
REFRESH=$(sed -n '/^\.method private final refresh()/,/^\.end method/p' "$MAIN")

if printf '%s\n' "$REFRESH" | grep -A1 'const-string v1, "\\u8bfb\\u53d6\\u63a7\\u4ef6' |
    grep -q 'move-object v5, v0'; then
    echo "FAIL: accessibility copy overwrites the LinearLayout register" >&2
    exit 1
fi

printf '%s\n' "$REFRESH" | grep -q 'const-string v0, "\\u8bfb\\u53d6\\u63a7\\u4ef6'

echo "PASS: MainActivity refresh preserves the row container register"
