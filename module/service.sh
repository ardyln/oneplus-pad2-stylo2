#!/system/bin/sh
# Installs the app (as a normal app: KernelSU without a mounting metamodule does not overlay
# system/), grants what it needs and starts the pen service.

MODDIR=${0%/*}
PKG=io.github.ardyln.stylo2
STATE_DIR=/data/adb/pad2_stylo2
APK="$MODDIR/Stylo2.apk"
log() { /system/bin/log -t pad2_stylo2 "$*"; }

until [ "$(getprop sys.boot_completed)" = "1" ]; do sleep 2; done
until [ "$(getprop sys.user.0.ce_available)" = "true" ]; do sleep 2; done
mkdir -p "$STATE_DIR"

# Installs an APK as a normal app and prints pm's output. pm hands its stdin and output to
# system_server, so neither may be a pipe (system_server keeps it open while an install waits, and
# $(...) never returns) nor a file under /data/adb (system_server may not use those fds: "Failed
# transaction"). The timeout keeps a stuck install from blocking the rest of the boot script.
install_apk() {
    _out=/data/local/tmp/pad2-pm-$$.out
    timeout 120 pm install -r -g "$1" </dev/null > "$_out" 2>&1
    cat "$_out" 2>/dev/null
    rm -f "$_out"
}

want=$(sha256sum "$APK" | cut -d' ' -f1)
if [ "$(cat "$STATE_DIR/app_sha256" 2>/dev/null)" != "$want" ] || ! pm path "$PKG" >/dev/null 2>&1; then
    # pm cannot read files under /data/adb; stage a copy it can read.
    cp -f "$APK" /data/local/tmp/stylo2.apk && chmod 0644 /data/local/tmp/stylo2.apk
    out=$(install_apk /data/local/tmp/stylo2.apk)
    rm -f /data/local/tmp/stylo2.apk
    log "install: $out"
    # Only remembered when it worked, so the next boot tries again otherwise
    echo "$out" | grep -q Success && echo "$want" > "$STATE_DIR/app_sha256"
fi

# Opening an app from the thumb button is a background activity start; this allows it.
appops set "$PKG" SYSTEM_ALERT_WINDOW allow
# Lets the app see which app is in front (notes-app features).
appops set "$PKG" GET_USAGE_STATS allow
# Lets the app turn its accessibility service back on: Android turns it off on every update.
pm grant "$PKG" android.permission.WRITE_SECURE_SETTINGS
# Keeps the pen connection alive in Doze. Undone by uninstall.sh (the app-ops go with the app).
cmd deviceidle whitelist +"$PKG" >/dev/null 2>&1
# The service is not exported; the app's boot receiver starts it.
am broadcast -n "$PKG/.BootReceiver" >/dev/null 2>&1
log "service started"

# Focused-window tracking for the notes-app features (an accessibility service that only reads
# the focused window's package). Appended to any services already enabled; removed by uninstall.sh.
SVC="$PKG/$PKG.FocusService"
cur=$(settings get secure enabled_accessibility_services)
case ":$cur:" in
    *":$SVC:"*) ;;
    *)
        if [ -z "$cur" ] || [ "$cur" = "null" ]; then new=$SVC; else new="$cur:$SVC"; fi
        settings put secure enabled_accessibility_services "$new"
        settings put secure accessibility_enabled 1
        log "accessibility service enabled"
        ;;
esac

# Root helper: virtual input devices, Saber's tool, the pen's presentation keys and its mouse.
sh "$MODDIR/stylus-bridge.sh" </dev/null >/dev/null 2>&1 &
