D=screenshots
adb shell am start -n com.android.settings/.Settings >/dev/null 2>&1; sleep 2
adb shell input tap 1160 735; sleep 1; adb shell input tap 886 1032; sleep 0.8; adb shell input tap 1160 735; sleep 2.5; adb exec-out screencap -p > $D/d_annotate_settings.png
