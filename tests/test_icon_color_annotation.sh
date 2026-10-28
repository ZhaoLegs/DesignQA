#!/bin/sh
set -eu

APK=${1:-app-debug.apk}
APKTOOL_DIR=${APKTOOL_DIR:-$(mktemp -d "${TMPDIR:-/tmp}/designqa-icon-color-test.XXXXXX")}
trap 'rm -rf "$APKTOOL_DIR"' EXIT

apktool d -f -o "$APKTOOL_DIR" "$APK" >/dev/null

SMALI=$(find "$APKTOOL_DIR" -path '*/com/legs/designqa/MeasureView.smali' -print -quit)
LABEL_PARTS=$(sed -n '/^\.method private final labelParts/,/^\.end method/p' "$SMALI")
IMAGE_BRANCH=$(printf '%s\n' "$LABEL_PARTS" | sed -n '/:pswitch_1/,/:pswitch_2/p')

printf '%s\n' "$IMAGE_BRANCH" | grep -q 'const-string .*"color"'
printf '%s\n' "$IMAGE_BRANCH" | grep -q 'MeasureView;->colorsOf(Landroid/graphics/Rect;)\[I'
printf '%s\n' "$IMAGE_BRANCH" | grep -q 'MeasureView;->hex(I)Ljava/lang/String;'
printf '%s\n' "$IMAGE_BRANCH" | grep -q 'Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;'

echo "PASS: image and icon annotations include sampled color"
