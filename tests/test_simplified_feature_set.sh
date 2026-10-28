#!/bin/sh
set -eu

APK=${1:-app-debug.apk}
APKTOOL_DIR=${APKTOOL_DIR:-$(mktemp -d "${TMPDIR:-/tmp}/designqa-simple-test.XXXXXX")}
trap 'rm -rf "$APKTOOL_DIR"' EXIT

apktool d -f -o "$APKTOOL_DIR" "$APK" >/dev/null

QA=$(find "$APKTOOL_DIR" -path '*/com/legs/designqa/QaService.smali' -print -quit)
MAIN=$(find "$APKTOOL_DIR" -path '*/com/legs/designqa/MainActivity.smali' -print -quit)
CLINIT=$(sed -n '/^\.method static constructor <clinit>/,/^\.end method/p' "$QA")
IS_ON=$(sed -n '/^\.method public final isOn/,/^\.end method/p' "$QA")
BUILD=$(sed -n '/^\.method private final buildPanel/,/^\.end method/p' "$QA")
ACTS=$(printf '%s\n' "$BUILD" | sed -n '/\.line 219/,/\.local v2, "acts"/p')
ON_CREATE=$(sed -n '/^\.method protected onCreate/,/^\.end method/p' "$MAIN")

printf '%s\n' "$ACTS" | grep -q '"inspect"'
printf '%s\n' "$ACTS" | grep -q '"annot"'
printf '%s\n' "$ACTS" | grep -q '"save"'

for removed in pick copy clear; do
    if printf '%s\n' "$ACTS" | grep -q "\"$removed\""; then
        echo "FAIL: removed tool is still visible: $removed" >&2
        exit 1
    fi
done

ICON_BLOCK=$(printf '%s\n' "$BUILD" | sed -n '/\.local v2, "k"/,/new-instance v3, Landroid\/widget\/TextView/p')
printf '%s\n' "$ICON_BLOCK" | grep -q '"inspect"'
printf '%s\n' "$ICON_BLOCK" | grep -q '"annot"'
printf '%s\n' "$ICON_BLOCK" | grep -q '"save"'
printf '%s\n' "$ICON_BLOCK" | grep -q 'Intrinsics;->areEqual'
ICON_BRANCHES=$(printf '%s\n' "$ICON_BLOCK" | sed -n 's/.*if-nez v3, \(:[^ ]*\).*/\1/p')
test "$(printf '%s\n' "$ICON_BRANCHES" | wc -l | tr -d ' ')" -eq 3
test "$(printf '%s\n' "$ICON_BRANCHES" | sort -u | wc -l | tr -d ' ')" -eq 1

CHUNK=$(printf '%s\n' "$BUILD" | sed -n '/check-cast v7, Ljava\/lang\/Iterable;/,/CollectionsKt;->chunked/p')
printf '%s\n' "$CHUNK" | grep -q 'const/4 v8, 0x1'
ROWS=$(printf '%s\n' "$BUILD" | sed -n '/\.line 246/,/\.local v1, "rows"/p')
printf '%s\n' "$ROWS" | grep -q 'QaService;->CHIPS:Ljava/util/List;'
printf '%s\n' "$ROWS" | grep -q 'const/4 v2, 0x3'
printf '%s\n' "$ROWS" | grep -q 'CollectionsKt;->chunked(Ljava/lang/Iterable;I)Ljava/util/List;'
test "$(printf '%s\n' "$BUILD" | grep -c 'const/16 .*0xb4')" -ge 2

if printf '%s\n' "$CLINIT" | grep -q '"mock"'; then
    echo "FAIL: design mockup is still listed in display controls" >&2
    exit 1
fi

printf '%s\n' "$IS_ON" | grep -q '"mock"'
printf '%s\n' "$IS_ON" | grep -q 'return v0'

if printf '%s\n' "$ON_CREATE" | grep -Fq '\u8bbe\u8ba1\u7a3f'; then
    echo "FAIL: main screen still shows the design mockup section" >&2
    exit 1
fi

if printf '%s\n' "$ON_CREATE" | grep -Fq '\u53d6\u8272'; then
    echo "FAIL: main screen still advertises color picking" >&2
    exit 1
fi

echo "PASS: popup keeps core actions and annotation controls only"
