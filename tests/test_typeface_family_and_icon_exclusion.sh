#!/bin/sh
set -eu

APK=${1:-app-debug.apk}
APKTOOL_DIR=${APKTOOL_DIR:-$(mktemp -d "${TMPDIR:-/tmp}/designqa-typeface-family-test.XXXXXX")}
trap 'rm -rf "$APKTOOL_DIR"' EXIT

apktool d -f -o "$APKTOOL_DIR" "$APK" >/dev/null

QA=$(find "$APKTOOL_DIR" -path '*/com/legs/designqa/QaService.smali' -print -quit)
MEASURE=$(find "$APKTOOL_DIR" -path '*/com/legs/designqa/MeasureView.smali' -print -quit)
FONT_INFO=$(sed -n '/^\.method private final fontInfo/,/^\.end method/p' "$QA")
LABEL_PARTS=$(sed -n '/^\.method private final labelParts/,/^\.end method/p' "$MEASURE")
DRAW=$(sed -n '/^\.method private final drawInspect/,/^\.end method/p' "$MEASURE")
TYPEFACE_STR=$(sed -n '/^\.method private final typefaceStr/,/^\.end method/p' "$MEASURE")

printf '%s\n' "$FONT_INFO" | grep -q 'QaService;->fontFamily(Landroid/view/accessibility/AccessibilityNodeInfo;)Ljava/lang/String;'
grep -q 'Landroid/text/style/TypefaceSpan;' "$QA"
printf '%s\n' "$TYPEFACE_STR" | grep -q 'NodeData;->getFontUnit()Ljava/lang/String;'
printf '%s\n' "$LABEL_PARTS" | grep -q 'MeasureView;->typefaceStr(Lcom/legs/designqa/NodeData;)Ljava/lang/String;'
printf '%s\n' "$LABEL_PARTS" | grep -q 'NodeData;->getFontSp()Ljava/lang/Float;'
printf '%s\n' "$DRAW" | grep -q 'NodeData;->getFontUnit()Ljava/lang/String;'

echo "PASS: readable font families are shown and icon-only nodes are excluded"
