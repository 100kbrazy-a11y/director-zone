#!/usr/bin/env bash
# Runs the CI-only UI tour (UiTour.kt) and takes a real emulator screenshot at every STEP marker.
set +e
mkdir -p results/ui
adb shell pm clear com.directorzone.app > /dev/null 2>&1
adb shell settings put system accelerometer_rotation 0
adb logcat -c
adb shell am start -n com.directorzone.app/com.directorzone.MainActivity --ez uitour true
declare -A got
for i in $(seq 1 400); do
  adb logcat -d -s UITOUR:I > /tmp/tour.txt 2>&1
  for s in $(grep -o "STEP [A-Za-z0-9_]*" /tmp/tour.txt | awk '{print $2}'); do
    if [ -z "${got[$s]}" ]; then
      got[$s]=1
      adb exec-out screencap -p > results/ui/$s.png
      case $s in *_full) ;; *) if command -v convert >/dev/null; then convert results/ui/$s.png -resize 540x results/ui/$s.png; fi;; esac
      echo "shot $s"
    fi
  done
  if grep -q "UITOUR *: *DONE" /tmp/tour.txt; then break; fi
  sleep 1
done
adb logcat -d > results/ui_full_logcat.txt 2>&1
adb logcat -d -s UITOUR:V AndroidRuntime:E DZ:E > results/ui_log.txt 2>&1
grep -c "FATAL EXCEPTION" results/ui_full_logcat.txt
ls results/ui | wc -l
