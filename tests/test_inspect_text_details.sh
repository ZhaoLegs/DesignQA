#!/bin/sh
set -eu

APK=${1:-app-debug.apk}
APKTOOL_DIR=${APKTOOL_DIR:-$(mktemp -d "${TMPDIR:-/tmp}/designqa-inspect-text-test.XXXXXX")}
trap 'rm -rf "$APKTOOL_DIR"' EXIT

apktool d -f -o "$APKTOOL_DIR" "$APK" >/dev/null

MEASURE=$(find "$APKTOOL_DIR" -path '*/com/legs/designqa/MeasureView.smali' -print -quit)
SERVICE=$(find "$APKTOOL_DIR" -path '*/com/legs/designqa/QaService.smali' -print -quit)
DRAW=$(sed -n '/^\.method private final drawInspect/,/^\.end method/p' "$MEASURE")
SET_MODE=$(sed -n '/^\.method public final setMode/,/^\.end method/p' "$SERVICE")
FAILED=0

FONT_DETAIL=$(printf '%s\n' "$DRAW" | sed -n '/\.line 537/,/const-string v0, "typeface"/p')
if printf '%s\n' "$FONT_DETAIL" | grep -q 'const-string v0, "font"'; then
    echo "FAIL: clicked text size is still gated by the font display mode" >&2
    FAILED=1
fi
printf '%s\n' "$FONT_DETAIL" | grep -q 'NodeData;->getFontSp()Ljava/lang/Float;'

COLOR_START=$(printf '%s\n' "$DRAW" | sed -n '/\.line 538/,/MeasureView;->colorsOf/p')
if ! printf '%s\n' "$COLOR_START" | grep -q 'NodeData;->getText()Ljava/lang/String;'; then
    echo "FAIL: clicked text color is still gated before checking for text content" >&2
    FAILED=1
fi
if ! printf '%s\n' "$COLOR_START" | grep -q 'if-nez v0'; then
    echo "FAIL: text content does not bypass the color display-mode gate" >&2
    FAILED=1
fi

TEXT_TO_BACKGROUND=$(printf '%s\n' "$DRAW" | sed -n '/\\u6587\\u5b57\\u8272/,/\\u80cc\\u666f\\u8272/p')
printf '%s\n' "$TEXT_TO_BACKGROUND" | grep -q 'const-string v1, "color"'
printf '%s\n' "$TEXT_TO_BACKGROUND" | grep -q 'MeasureView;->on(Ljava/lang/String;)Z'

CAPTURE_GATE=$(printf '%s\n' "$SET_MODE" | sed -n '/MeasureView\$Mode;->INSPECT/,/QaService;->capture\$default/p')
if printf '%s\n' "$CAPTURE_GATE" | grep -q 'const-string v4, "color"'; then
    echo "FAIL: inspect capture still depends on the color display mode" >&2
    FAILED=1
fi

test "$FAILED" -eq 0
echo "PASS: clicked text always shows size and sampled text color"
