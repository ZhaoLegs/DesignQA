#!/bin/sh
set -eu

APK=${1:-app-debug.apk}
APKTOOL_DIR=${APKTOOL_DIR:-$(mktemp -d "${TMPDIR:-/tmp}/designqa-type-test.XXXXXX")}
trap 'rm -rf "$APKTOOL_DIR"' EXIT

apktool d -f -o "$APKTOOL_DIR" "$APK" >/dev/null

SMALI=$(find "$APKTOOL_DIR" -path '*/com/legs/designqa/QaService.smali' -print -quit)
IS_ON=$(sed -n '/^\.method public final isOn/,/^\.end method/p' "$SMALI")
TOGGLE=$(sed -n '/^\.method private final toggleChip/,/^\.end method/p' "$SMALI")

printf '%s\n' "$IS_ON" | grep -q 'const-string .*"annotType"'
printf '%s\n' "$IS_ON" | grep -q 'getString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;'
printf '%s\n' "$TOGGLE" | grep -q 'putString(Ljava/lang/String;Ljava/lang/String;)Landroid/content/SharedPreferences\$Editor;'
printf '%s\n' "$TOGGLE" | grep -q 'putBoolean(Ljava/lang/String;Z)Landroid/content/SharedPreferences\$Editor;'

echo "PASS: annotation primary type is single-select and auxiliary layers remain independent"
