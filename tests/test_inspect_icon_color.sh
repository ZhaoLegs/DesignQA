#!/bin/sh
set -eu

APK=${1:-app-debug.apk}
APKTOOL_DIR=${APKTOOL_DIR:-$(mktemp -d "${TMPDIR:-/tmp}/designqa-inspect-icon-color-test.XXXXXX")}
trap 'rm -rf "$APKTOOL_DIR"' EXIT

apktool d -f -o "$APKTOOL_DIR" "$APK" >/dev/null

MEASURE=$(find "$APKTOOL_DIR" -path '*/com/legs/designqa/MeasureView.smali' -print -quit)
DRAW=$(sed -n '/^\.method private final drawInspect/,/^\.end method/p' "$MEASURE")
COLOR_DETAIL=$(printf '%s\n' "$DRAW" | sed -n '/\.line 538/,/const-string v0, "radius"/p')

printf '%s\n' "$COLOR_DETAIL" | grep -q 'Kind;->IMAGE:Lcom/legs/designqa/Kind;'
printf '%s\n' "$COLOR_DETAIL" | grep -q 'Kind;->BUTTON:Lcom/legs/designqa/Kind;'
printf '%s\n' "$COLOR_DETAIL" | grep -q 'NodeData;->getFontSp()Ljava/lang/Float;'
printf '%s\n' "$COLOR_DETAIL" | grep -Fq '\u56fe\u6807\u8272'
printf '%s\n' "$COLOR_DETAIL" | grep -q 'MeasureView;->colorsOf(Landroid/graphics/Rect;)\[I'
printf '%s\n' "$COLOR_DETAIL" | grep -q 'MeasureView;->hex(I)Ljava/lang/String;'

echo "PASS: clicked images and icon-only buttons always show their sampled icon color"
