#!/system/bin/sh
# Root helper for the app (see app/.../StylusBridge.kt):
# - pipes the app's stylus_button.fifo into Android's `uinput` tool, which creates a virtual
#   external stylus whose button Android merges into the pen's events. When the app closes
#   the pipe, `uinput` exits and the device goes away, so a held button can't get stuck.
# - publishes Saber's active tool (its "lastTool" pref) to the app's saber_tool file.

PKG=io.github.ardyln.stylo2
FILES=/data/data/$PKG/files
SABER_PREFS=/data/data/com.adilhanney.saber/shared_prefs/FlutterSharedPreferences.xml
BB=/data/adb/ksu/bin/busybox
[ -x "$BB" ] || BB=/data/adb/magisk/busybox
log() { /system/bin/log -t pad2_stylo2 "$*"; }

button_loop() {
    while true; do
        if [ -p "$FILES/stylus_button.fifo" ]; then
            uinput - < "$FILES/stylus_button.fifo" >/dev/null 2>&1
            sleep 1
        else
            sleep 5
        fi
    done
}

# Saber stores the ToolId index; it omits the default (1, fountain pen).
saber_tool() {
    v=$(grep -oE 'flutter.lastTool" value="[0-9]+"' "$SABER_PREFS" 2>/dev/null | grep -oE '[0-9]+')
    echo "${v:-1}"
}

publish_tool() {
    [ -f "$FILES/saber_tool" ] || return
    v=$(saber_tool)
    [ "$(cat "$FILES/saber_tool" 2>/dev/null)" = "$v" ] && return
    echo "$v" > "$FILES/saber_tool"
}

tool_loop() {
    while true; do
        if [ ! -f "$SABER_PREFS" ] || [ ! -d "$FILES" ]; then
            sleep 30
            continue
        fi
        publish_tool
        if [ -x "$BB" ]; then
            # Saber's prefs file is rewritten on every tool change; the app recreates its file on first start.
            "$BB" inotifyd - "${SABER_PREFS%/*}:wy" "$FILES:n" | while read -r _ _ _; do publish_tool; done
            sleep 2
        else
            sleep 1
        fi
    done
}

log "stylus bridge started"
button_loop &
tool_loop
