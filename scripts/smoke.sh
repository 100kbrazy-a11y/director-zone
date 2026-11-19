#!/usr/bin/env bash
# Installs the APK on the emulator, opens the app, taps around Home, and records crashes + a screenshot.
set +e
adb install -r Director-Zone.apk
adb logcat -c
adb shell am start -n com.directorzone.app/com.directorzone.MainActivity
sleep 10
adb exec-out screencap -p > home.png
adb shell input swipe 540 1500 540 600 400; sleep 2
adb exec-out screencap -p > home2.png
adb logcat -d > logcat.txt
if grep -q "FATAL EXCEPTION" logcat.txt; then echo "CRASH:"; grep -A25 "FATAL EXCEPTION" logcat.txt | head -60; echo crashed > result.txt; else echo "no crash"; echo ok > result.txt; fi
adb shell pidof com.directorzone.app || echo "process not running"
