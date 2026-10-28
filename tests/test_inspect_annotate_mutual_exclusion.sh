#!/bin/sh
set -eu

APK=${1:-app-debug.apk}
APKTOOL_DIR=${APKTOOL_DIR:-$(mktemp -d "${TMPDIR:-/tmp}/designqa-mode-exclusion-test.XXXXXX")}
trap 'rm -rf "$APKTOOL_DIR"' EXIT

apktool d -f -o "$APKTOOL_DIR" "$APK" >/dev/null

QA=$(find "$APKTOOL_DIR" -path '*/com/legs/designqa/QaService.smali' -print -quit)
SET_MODE=$(sed -n '/^\.method public final setMode/,/^\.end method/p' "$QA")
TOGGLE=$(sed -n '/^\.method private final toggleAnnotate/,/^\.end method/p' "$QA")
ENABLE_ANNOT=$(printf '%s\n' "$TOGGLE" | sed -n '/:cond_1/,/QaService;->collectAnnots/p')

printf '%s\n' "$SET_MODE" | grep -q 'MeasureView;->clearSel()V'

if ! printf '%s\n' "$ENABLE_ANNOT" | grep -q 'MeasureView\$Mode;->NONE'; then
    echo "FAIL: enabling full-screen annotations does not exit inspect mode" >&2
    exit 1
fi

printf '%s\n' "$ENABLE_ANNOT" | grep -q 'QaService;->setMode(Lcom/legs/designqa/MeasureView\$Mode;)V'

echo "PASS: inspect and full-screen annotation modes are mutually exclusive"
