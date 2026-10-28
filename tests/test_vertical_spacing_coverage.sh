#!/bin/sh
set -eu

APK=${1:-app-debug.apk}
APKTOOL_DIR=${APKTOOL_DIR:-$(mktemp -d "${TMPDIR:-/tmp}/designqa-vertical-spacing-test.XXXXXX")}
trap 'rm -rf "$APKTOOL_DIR"' EXIT

apktool d -f -o "$APKTOOL_DIR" "$APK" >/dev/null

VIEW=$(find "$APKTOOL_DIR" -path '*/com/legs/designqa/MeasureView.smali' -print -quit)
FILTER=$(find "$APKTOOL_DIR" -path '*/com/legs/designqa/RepeatFilter.smali' -print -quit)
LAYOUT=$(sed -n '/^\.method private final layoutAnnots/,/^\.end method/p' "$VIEW")
SPACING=$(sed -n '/^\.method private final spacingAnnotations/,/^\.end method/p' "$VIEW")
GAPS=$(sed -n '/^\.method public static representativeGaps/,/^\.end method/p' "$FILTER")

if [ -z "$SPACING" ]; then
    echo "FAIL: spacing annotations still use the generic ten-label budget" >&2
    exit 1
fi

printf '%s\n' "$SPACING" | grep -q 'Lcom/legs/designqa/Gap;'
printf '%s\n' "$SPACING" | grep -q 'const-string .*"space"'
printf '%s\n' "$LAYOUT" | grep -q 'MeasureView;->spacingAnnotations(Ljava/util/List;)Ljava/util/List;'

if printf '%s\n' "$GAPS" | grep -q 'sub-int/2addr'; then
    echo "FAIL: gap identity still collapses equal lengths at different coordinates" >&2
    exit 1
fi
test "$(printf '%s\n' "$GAPS" | grep -c 'StringBuilder;->append(I)')" -ge 3

echo "PASS: vertical spacing keeps every distinct screen interval"
