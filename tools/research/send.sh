#!/usr/bin/env bash
# usage: send.sh cmd[,cmd...]  - restarts penprobe and sends allowlisted pen commands, prints results
M=$(cat "${PEN_MAC_FILE:-$HOME/tools/stylo2-re/pen.mac}")
adb shell input keyevent KEYCODE_WAKEUP; adb logcat -c
adb shell su -c "'am start -S -n local.penprobe/.ProbeActivity --es mac $M --es cmd $1'" >/dev/null
sleep 6; adb logcat -d -s PenProbe:I | grep -E 'WRITE|WROTE|refusing|failed' | sed -E 's/^.*PenProbe: //'
