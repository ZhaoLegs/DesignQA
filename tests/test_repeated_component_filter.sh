#!/bin/sh
set -eu

APK=${1:-app-debug.apk}
APKTOOL_DIR=${APKTOOL_DIR:-$(mktemp -d "${TMPDIR:-/tmp}/designqa-repeat-test.XXXXXX")}
trap 'rm -rf "$APKTOOL_DIR"' EXIT

apktool d -f -o "$APKTOOL_DIR" "$APK" >/dev/null

QA=$(find "$APKTOOL_DIR" -path '*/com/legs/designqa/QaService.smali' -print -quit)
FILTER=$(find "$APKTOOL_DIR" -path '*/com/legs/designqa/RepeatFilter.smali' -print -quit)
COLLECT=$(sed -n '/^\.method private final collectAnnots/,/^\.end method/p' "$QA")

test -n "$FILTER"
printf '%s\n' "$COLLECT" | grep -q 'RepeatFilter;->representatives'
printf '%s\n' "$COLLECT" | grep -q 'RepeatFilter;->representativeGaps'
grep -q 'NodeData;->getRect()Landroid/graphics/Rect;' "$FILTER"
grep -q 'NodeData;->getParent()Landroid/graphics/Rect;' "$FILTER"
grep -q 'NodeData;->getKind()Lcom/legs/designqa/Kind;' "$FILTER"
grep -q 'NodeData;->getFontSp()Ljava/lang/Float;' "$FILTER"
grep -q 'HashSet;->add(Ljava/lang/Object;)Z' "$FILTER"
grep -q '^\.method public static representativeGaps(Ljava/util/List;)Ljava/util/List;' "$FILTER"
grep -q 'Gap;->getVertical()Z' "$FILTER"
grep -q 'Gap;->getPos()I' "$FILTER"
grep -q 'Gap;->getA()I' "$FILTER"
grep -q 'Gap;->getB()I' "$FILTER"

if grep -q 'NodeData;->getText()Ljava/lang/String;' "$FILTER"; then
    echo "FAIL: visible text must not define repeated component identity" >&2
    exit 1
fi

echo "PASS: repeated components use one structural representative"
