#!/usr/bin/env bash
# Prints button events and console lines (except periodic rx stats) since the last logcat clear, then clears.
adb logcat -d -s PenProbe:I | grep NOTIFY | sed -E 's/^.*NOTIFY ([0-9a-f]{8})[^ ]* t=([0-9]+) ([0-9a-f]+)$/\2 \1 \3/' | while read t u h; do
  case $u in
    180938ac) echo "$t BUTTON $h";;
    6e400003|6e400004) s=$(echo "$h" | xxd -r -p | tr -d '\r\n'); case "$s" in rx*) ;; *) echo "$t console: $s";; esac;;
    *) echo "$t $u $h";;
  esac
done
adb logcat -c
