#!/bin/sh
set -eu

APK=${1:-app-debug.apk}
APKTOOL_DIR=${APKTOOL_DIR:-$(mktemp -d "${TMPDIR:-/tmp}/designqa-popup-layout-test.XXXXXX")}
trap 'rm -rf "$APKTOOL_DIR"' EXIT

apktool d -f -o "$APKTOOL_DIR" "$APK" >/dev/null

QA=$(find "$APKTOOL_DIR" -path '*/com/legs/designqa/QaService.smali' -print -quit)
BUILD=$(sed -n '/^\.method private final buildPanel/,/^\.end method/p' "$QA")
TILE_PADDING=$(printf '%s\n' "$BUILD" | sed -n '/\.line 232/,/\.line 233/p')
FAILED=0

if printf '%s\n' "$BUILD" | grep -Fq '\u8bbe\u8ba1\u8d70\u67e5'; then
    echo "FAIL: popup header still shows the design-walkthrough subtitle" >&2
    FAILED=1
fi

if ! printf '%s\n' "$TILE_PADDING" | grep -q 'const/16 v8, 0x9'; then
    echo "FAIL: primary action top padding is not centered" >&2
    FAILED=1
fi

if ! printf '%s\n' "$TILE_PADDING" | grep -q 'const/16 v9, 0x9'; then
    echo "FAIL: primary action bottom padding is not centered" >&2
    FAILED=1
fi

test "$FAILED" -eq 0
echo "PASS: popup header is concise and primary action text is vertically centered"
