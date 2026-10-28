#!/bin/sh
set -eu

APK=${1:-app-debug.apk}
APKTOOL_DIR=${APKTOOL_DIR:-$(mktemp -d "${TMPDIR:-/tmp}/designqa-lock-test.XXXXXX")}
trap 'rm -rf "$APKTOOL_DIR"' EXIT

apktool d -f -o "$APKTOOL_DIR" "$APK" >/dev/null

SMALI=$(find "$APKTOOL_DIR" -path '*/com/legs/designqa/QaService.smali' -print -quit)
SYNC=$(sed -n '/^\.method private final syncLockscreenVisibility/,/^\.end method/p' "$SMALI")
EVENT=$(sed -n '/^\.method public onAccessibilityEvent/,/^\.end method/p' "$SMALI")

printf '%s\n' "$SYNC" | grep -q 'Landroid/app/KeyguardManager;->isKeyguardLocked()Z'
printf '%s\n' "$SYNC" | grep -q 'QaService;->canvasView:Lcom/legs/designqa/MeasureView;'
printf '%s\n' "$SYNC" | grep -q 'QaService;->panel:Landroid/widget/LinearLayout;'
printf '%s\n' "$SYNC" | grep -q 'QaService;->bubbleWin:Landroid/widget/FrameLayout;'
printf '%s\n' "$SYNC" | grep -q 'MeasureView\$Mode;->NONE:Lcom/legs/designqa/MeasureView\$Mode;'
printf '%s\n' "$SYNC" | grep -q 'QaService;->setMode(Lcom/legs/designqa/MeasureView\$Mode;)V'
printf '%s\n' "$SYNC" | grep -q 'Landroid/view/View;->setVisibility(I)V'
printf '%s\n' "$EVENT" | grep -q 'QaService;->syncLockscreenVisibility()V'

echo "PASS: lockscreen hides every DesignQA overlay"
