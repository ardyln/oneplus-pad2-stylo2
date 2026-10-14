# OnePlus Stylo 2 for the OnePlus Pad 2 on AOSP ROMs

A KernelSU/Magisk module with a small app that brings back two OxygenOS features of the OnePlus Stylo 2
on AOSP-based ROMs (tested on PixelOS 17 for `caihong`):

- **Writing feedback** ("textured vibration"): the pen vibrates while you write, in every app. Choose
  the texture (pencil, eraser, ballpoint pen, pen) and the strength.
- **Thumb button**: open an app of your choice, optionally with a short vibration as confirmation.

It also shows the pen's battery level in its notification.

## How it works

The app keeps a Bluetooth LE connection to the paired pen. Each time the pen connects, it sends the
writing feedback setting and subscribes to the button. See [docs/PROTOCOL.md](docs/PROTOCOL.md).
No OnePlus code or apps are used.

## Install

1. Pair the pen with the tablet as usual.
2. Install the module zip with KernelSU or Magisk and reboot.
3. Open the **Stylo 2** app to choose the texture, strength and button action.

The module installs the app, allows it to open apps from the background (for the button action) and
exempts it from battery optimization. Removing the module undoes all of this.

## Build

`KS_PASS=<keystore password> tools/build.sh` (needs the Android SDK build tools, platform 34 and a JDK).
The signing key is created on the first build at `~/tools/stylo2-release.jks` and is not part of the repo.
