D=screenshots
adb logcat -b crash -c; adb logcat -c; sh inst.sh; sleep 3; adb shell input keyevent KEYCODE_WAKEUP
adb shell am start -n com.legs.designqa/.MainActivity >/dev/null 2>&1; sleep 2; adb exec-out screencap -p > $D/a_main.png
adb shell am start -n com.android.settings/.Settings >/dev/null 2>&1; sleep 2; adb shell input tap 1160 735; sleep 1.2; adb exec-out screencap -p > $D/b_settings_panel.png
adb shell input tap 231 1015; sleep 0.8; adb shell input tap 1160 735; sleep 0.8; adb shell input tap 1085 1106; sleep 2; adb exec-out screencap -p > $D/c_inspect_button.png
adb shell input tap 1160 735; sleep 0.8; adb shell input tap 231 1015; sleep 0.8; adb shell input tap 886 1032; sleep 0.8; adb shell input tap 1160 735; sleep 2.5; adb exec-out screencap -p > $D/d_annotate_settings.png
adb logcat -b crash -d | tail -5; adb logcat -d -s Spec | tail -3
cd $D && rm -f ../shots.zip && zip -q ../shots.zip a_main.png b_settings_panel.png c_inspect_button.png d_annotate_settings.png
