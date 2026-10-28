#!/bin/sh
set -eu

APK=${1:-app-debug.apk}
APKTOOL_DIR=${APKTOOL_DIR:-$(mktemp -d "${TMPDIR:-/tmp}/designqa-popup-controls-test.XXXXXX")}
trap 'rm -rf "$APKTOOL_DIR"' EXIT

apktool d -f -o "$APKTOOL_DIR" "$APK" >/dev/null

QA=$(find "$APKTOOL_DIR" -path '*/com/legs/designqa/QaService.smali' -print -quit)
CLINIT=$(sed -n '/^\.method static constructor <clinit>/,/^\.end method/p' "$QA")
BUILD=$(sed -n '/^\.method private final buildPanel/,/^\.end method/p' "$QA")
ROWS=$(printf '%s\n' "$BUILD" | sed -n '/\.line 246/,/\.local v1, "rows"/p')

for key in size space font typeface color bounds grid radius; do
    printf '%s\n' "$CLINIT" | grep -q "\"$key\""
done

if printf '%s\n' "$ROWS" | grep -q 'CollectionsKt;->emptyList()Ljava/util/List;'; then
    echo "FAIL: annotation controls are hidden by an empty row list" >&2
    exit 1
fi

printf '%s\n' "$ROWS" | grep -q 'QaService;->CHIPS:Ljava/util/List;'
printf '%s\n' "$ROWS" | grep -q 'check-cast v1, Ljava/lang/Iterable;'
printf '%s\n' "$ROWS" | grep -q 'const/4 v2, 0x3'
printf '%s\n' "$ROWS" | grep -q 'CollectionsKt;->chunked(Ljava/lang/Iterable;I)Ljava/util/List;'

grep -q 'QaService;->toggleChip(Ljava/lang/String;)V' "$QA"

echo "PASS: popup renders all annotation controls in three-column rows"
