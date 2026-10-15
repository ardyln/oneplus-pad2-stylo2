#!/system/bin/sh
# Install-time checks (sourced by the KernelSU/Magisk installer).

DEV="$(getprop ro.custom.device) $(getprop ro.product.vendor.device) $(getprop ro.product.device)"
case "$DEV" in
    *caihong*|*OP5DAAL1*) ui_print "- Device: OnePlus Pad 2 ($DEV)" ;;
    *) abort "! This module is only for the OnePlus Pad 2 (caihong); found: $DEV" ;;
esac

set_perm_recursive "$MODPATH" 0 0 0755 0644
for f in service.sh uninstall.sh stylus-bridge.sh; do
    set_perm "$MODPATH/$f" 0 0 0755
done
ui_print "- After reboot, open the Stylo 2 app to set up writing feel, shortcuts and presentation mode."
