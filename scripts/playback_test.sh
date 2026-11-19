#!/usr/bin/env bash
# Runs the in-app playback self-test (DzTest.kt) on the emulator and collects logs + captured frames into ./results
set +e
mkdir -p results
adb install -r Director-Zone-test.apk > results/install.txt 2>&1
adb logcat -c
adb shell am start -n com.directorzone.app/com.directorzone.MainActivity --ez dztest true
for i in $(seq 1 110); do
  if adb logcat -d -s DZTEST:I | grep -q "DZTEST *: *DONE\| DONE$"; then break; fi
  sleep 5
done
adb logcat -d -s DZTEST:V DZ:V AndroidRuntime:E MediaCodec:W ExoPlayerImpl:W Transformer:V > results/log.txt 2>&1
adb logcat -d > results/full_logcat.txt 2>&1
adb pull /sdcard/Android/data/com.directorzone.app/files/dztest_out results/frames > results/pull.txt 2>&1
adb exec-out screencap -p > results/final_screen.png
grep -E "RESULT|SUMMARY|PASS|FAIL|READY|PROJECT|SPEED|EXPORT|ERROR|CRASH" results/log.txt | head -80
ls results/frames 2>/dev/null | head -40
