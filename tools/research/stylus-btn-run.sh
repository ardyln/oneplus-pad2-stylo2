#!/system/bin/sh
# Runs the virtual stylus-button test, vibrates the tablet at the start and end of the
# pressed phase, and prints every change of Saber's lastTool with a timestamp.
F=/data/data/com.adilhanney.saber/shared_prefs/FlutterSharedPreferences.xml
uinput /data/local/tmp/stylus-btn-test.json >/dev/null 2>&1 &
start=$(date +%s)
last=x
buzzed1=; buzzed2=
while true; do
  t=$(( $(date +%s) - start ))
  [ $t -ge 8 ] && [ -z "$buzzed1" ] && { cmd vibrator_manager synced oneshot 400 >/dev/null 2>&1; echo "t=$t pressed phase starts"; buzzed1=1; }
  [ $t -ge 33 ] && [ -z "$buzzed2" ] && { cmd vibrator_manager synced oneshot 400 >/dev/null 2>&1; echo "t=$t released"; buzzed2=1; }
  v=$(grep -oE 'lastTool. value=.[0-9]+' $F | grep -oE '[0-9]+$'); v=${v:-1}
  [ "$v" != "$last" ] && { echo "t=$t lastTool=$v"; last=$v; }
  [ $t -ge 36 ] && break
  sleep 0.2
done
