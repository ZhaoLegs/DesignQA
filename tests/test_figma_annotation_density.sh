#!/bin/sh
set -eu

APK=${1:-app-debug.apk}
APKTOOL_DIR=${APKTOOL_DIR:-$(mktemp -d "${TMPDIR:-/tmp}/designqa-density-test.XXXXXX")}
trap 'rm -rf "$APKTOOL_DIR"' EXIT

apktool d -f -o "$APKTOOL_DIR" "$APK" >/dev/null

SMALI=$(find "$APKTOOL_DIR" -path '*/com/legs/designqa/MeasureView.smali' -print -quit)
BUDGET=$(sed -n '/^\.method private final annotationBudget/,/^\.end method/p' "$SMALI")
LAYOUT=$(sed -n '/^\.method private final layoutAnnots/,/^\.end method/p' "$SMALI")
LABEL_PARTS=$(sed -n '/^\.method private final labelParts/,/^\.end method/p' "$SMALI")
DRAW=$(sed -n '/^\.method private final drawAnnots/,/^\.end method/p' "$SMALI")

if [ -z "$BUDGET" ]; then
    echo "FAIL: annotation label budget is missing" >&2
    exit 1
fi
printf '%s\n' "$BUDGET" | grep -q 'const/16 .*0xa'
printf '%s\n' "$BUDGET" | grep -q 'div-int'
test "$(printf '%s\n' "$LAYOUT" | grep -c 'MeasureView;->annotationBudget(Ljava/util/List;)Ljava/util/List;')" -ge 1
printf '%s\n' "$LAYOUT" | grep -q 'invoke-direct {v0, v1}, Lcom/legs/designqa/MeasureView;->annotationBudget(Ljava/util/List;)Ljava/util/List;'
printf '%s\n' "$LAYOUT" | grep -q 'MeasureView;->spacingAnnotations(Ljava/util/List;)Ljava/util/List;'

if printf '%s\n' "$LABEL_PARTS" | grep -q '<48dp'; then
    echo "FAIL: full-screen labels still include touch-target warnings" >&2
    exit 1
fi

printf '%s\n' "$DRAW" | grep -q 'const v.*0x66000000'

echo "PASS: full-screen annotations use a sparse Figma-style label budget"
