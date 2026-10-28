#!/bin/sh
set -eu

APK=${1:-app-debug.apk}
APKTOOL_DIR=${APKTOOL_DIR:-$(mktemp -d "${TMPDIR:-/tmp}/designqa-typeface-test.XXXXXX")}
trap 'rm -rf "$APKTOOL_DIR"' EXIT

apktool d -f -o "$APKTOOL_DIR" "$APK" >/dev/null

QA_SERVICE=$(find "$APKTOOL_DIR" -path '*/com/legs/designqa/QaService.smali' -print -quit)
MEASURE_VIEW=$(find "$APKTOOL_DIR" -path '*/com/legs/designqa/MeasureView.smali' -print -quit)
MAIN_SWITCH=$(find "$APKTOOL_DIR" -path '*/com/legs/designqa/MainActivity$onCreate$5$sw$1$1.smali' -print -quit)

grep -q 'const-string .*"typeface"' "$QA_SERVICE"
grep -q 'const-string .*"\\u5b57\\u4f53"' "$QA_SERVICE"
sed -n '/^\.method public final isOn/,/^\.end method/p' "$QA_SERVICE" |
    grep -q 'const-string .*"typeface"'
sed -n '/^\.method private final toggleChip/,/^\.end method/p' "$QA_SERVICE" |
    grep -q 'const-string .*"typeface"'

grep -q 'const-string .*"\\u5b57\\u4f53\\u540d\\u4e0d\\u53ef\\u8bfb\\u53d6"' "$MEASURE_VIEW"
grep -q 'const-string .*"\\u7cfb\\u7edf\\u672a\\u63d0\\u4f9b\\u5b57\\u4f53\\u540d"' "$MEASURE_VIEW"

grep -q 'const-string .*"annotType"' "$MAIN_SWITCH"
grep -q 'putString(Ljava/lang/String;Ljava/lang/String;)Landroid/content/SharedPreferences\$Editor;' "$MAIN_SWITCH"

echo "PASS: typeface mode is selectable and reports Android font-name limitations honestly"
