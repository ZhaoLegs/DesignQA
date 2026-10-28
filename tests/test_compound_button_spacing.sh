#!/bin/sh
set -eu

APK=${1:-app-debug.apk}
APKTOOL_DIR=${APKTOOL_DIR:-$(mktemp -d "${TMPDIR:-/tmp}/designqa-compound-gap-test.XXXXXX")}
trap 'rm -rf "$APKTOOL_DIR"' EXIT

apktool d -f -o "$APKTOOL_DIR" "$APK" >/dev/null

QA=$(find "$APKTOOL_DIR" -path '*/com/legs/designqa/QaService.smali' -print -quit)
ANCHOR=$(sed -n '/^\.method private static final spacingAnchor/,/^\.end method/p' "$QA")
COLLECT=$(sed -n '/^\.method private static final collectAnnots\$w\$58/,/^\.end method/p' "$QA")

if [ -z "$ANCHOR" ]; then
    echo "FAIL: compound button spacing anchor is missing" >&2
    exit 1
fi

printf '%s\n' "$ANCHOR" | grep -q 'Kind;->BUTTON'
printf '%s\n' "$ANCHOR" | grep -q 'AccessibilityNodeInfo;->getText()Ljava/lang/CharSequence;'
printf '%s\n' "$ANCHOR" | grep -q 'StringsKt;->isBlank'
printf '%s\n' "$ANCHOR" | grep -q 'Ljava/util/ArrayList;->size()I'
printf '%s\n' "$ANCHOR" | grep -q 'Ljava/util/ArrayList;->get(I)Ljava/lang/Object;'
printf '%s\n' "$COLLECT" | grep -q 'QaService;->spacingAnchor'

echo "PASS: icon-only sibling buttons expose their visual spacing"
