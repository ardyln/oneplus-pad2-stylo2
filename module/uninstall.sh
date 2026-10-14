#!/system/bin/sh
# Runs early in the boot after the module is marked for removal; the app can only be removed
# once the package manager is up, so that part runs in the background.

(
    until [ "$(getprop sys.boot_completed)" = "1" ]; do sleep 2; done
    sleep 10
    cmd deviceidle whitelist -io.github.ardyln.stylo2 >/dev/null 2>&1
    SVC=io.github.ardyln.stylo2/io.github.ardyln.stylo2.FocusService
    cur=$(settings get secure enabled_accessibility_services)
    new=$(echo "$cur" | tr ':' '\n' | grep -vxF "$SVC" | paste -sd: -)
    if [ "$new" != "$cur" ]; then
        if [ -z "$new" ]; then settings delete secure enabled_accessibility_services
        else settings put secure enabled_accessibility_services "$new"; fi
    fi
    pm uninstall io.github.ardyln.stylo2 >/dev/null 2>&1
    rm -rf /data/adb/pad2_stylo2
) </dev/null >/dev/null 2>&1 &
