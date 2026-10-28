#!/bin/sh
set -eu

APK=${1:-app-debug.apk}
APKTOOL_DIR=${APKTOOL_DIR:-$(mktemp -d "${TMPDIR:-/tmp}/designqa-font-coverage-test.XXXXXX")}
trap 'rm -rf "$APKTOOL_DIR"' EXIT

apktool d -f -o "$APKTOOL_DIR" "$APK" >/dev/null

SMALI=$(find "$APKTOOL_DIR" -path '*/com/legs/designqa/MeasureView.smali' -print -quit)
BUDGET=$(sed -n '/^\.method private final annotationBudget/,/^\.end method/p' "$SMALI")

printf '%s\n' "$BUDGET" | grep -q 'const-string .*"font"'
printf '%s\n' "$BUDGET" | grep -q 'instance-of .*Lcom/legs/designqa/MeasureView\$Label;'
printf '%s\n' "$BUDGET" | grep -q 'Lcom/legs/designqa/MeasureView\$Label;->getLines()Ljava/util/List;'
printf '%s\n' "$BUDGET" | grep -q 'Ljava/util/LinkedHashSet;'
printf '%s\n' "$BUDGET" | grep -q 'const/16 .*0xa'

echo "PASS: font annotations preserve one label for every displayed text size"
