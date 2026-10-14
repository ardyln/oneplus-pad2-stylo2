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

want=$(sha256sum "$APK" | cut -d' ' -f1)
if [ "$(cat "$STATE_DIR/app_sha256" 2>/dev/null)" != "$want" ] || ! pm path "$PKG" >/dev/null 2>&1; then
    # pm cannot read files under /data/adb; stage a copy it can read.
    cp -f "$APK" /data/local/tmp/stylo2.apk && chmod 0644 /data/local/tmp/stylo2.apk
    log "install: $(pm install -r -g /data/local/tmp/stylo2.apk 2>&1)"
    rm -f /data/local/tmp/stylo2.apk
    echo "$want" > "$STATE_DIR/app_sha256"
fi

# Opening an app from the thumb button is a background activity start; this allows it.
appops set "$PKG" SYSTEM_ALERT_WINDOW allow
# Keeps the pen connection alive in Doze. Both are undone by uninstall.sh.
cmd deviceidle whitelist +"$PKG" >/dev/null 2>&1
# The service is not exported; the app's boot receiver starts it.
am broadcast -n "$PKG/.BootReceiver" >/dev/null 2>&1
log "service started"
