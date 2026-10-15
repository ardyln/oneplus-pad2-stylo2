# OnePlus Stylo 2 for the OnePlus Pad 2 on AOSP ROMs

A KernelSU/Magisk module with a small app that brings back two OxygenOS features of the OnePlus Stylo 2
on AOSP-based ROMs (tested on PixelOS 17 for `caihong`):

- **Writing feedback** ("textured vibration"): the pen vibrates while you write, in every app. Choose
  the texture (pencil, eraser, ballpoint pen, pen) and the strength.
- **Thumb-button shortcuts**: the pen reports double taps; chain them (double tap, double tap ×2, …)
  and give each its own action: open an app, media keys, a broadcast intent (Tasker and similar apps)
  or a Home Assistant webhook, next/previous page, and switching the app's own settings.
- **Quick Settings tiles**: presentation mode, writing feel on/off, and the pen texture.
- **Notes apps (Saber)**: in [Saber](https://github.com/saber-notes/saber), a double tap toggles the
  eraser, and the texture follows the active tool, with a texture and strength per tool.

- **Presentation mode**: the touch area near the tip becomes a remote: touch and move the pen to move
  the pointer (air mouse), and give each gesture (swipe toward or away from the tip, single tap, double
  tap) an action: scroll with a chosen strength, click, next/previous page, or any shortcut action.

It also shows the pen's battery level and whether it's on the magnetic charger, and has a live event
log for troubleshooting.

## How it works

The app keeps a Bluetooth LE connection to the paired pen. Each time the pen connects, it sends the
writing feedback setting and subscribes to the button. See [docs/PROTOCOL.md](docs/PROTOCOL.md) for
the protocol, [docs/RESEARCH.md](docs/RESEARCH.md) for how it was worked out, and
[docs/STYLO2.md](docs/STYLO2.md) for the original research log.
No OnePlus code or apps are used.

## Install

1. Pair the pen with the tablet as usual.
2. Install the module zip with KernelSU or Magisk and reboot.
3. Open the **Stylo 2** app to choose the texture, strength, shortcuts and notes-app options.

At boot the module:
- installs the app and grants it background activity starts (to open apps from a shortcut), usage
  access, and an exemption from battery optimization;
- enables the app's accessibility service "Stylo 2: active app", which only reads the package name of
  the focused window (to know when Saber is in use, also in desktop windowing mode);
- starts a root helper (`stylus-bridge.sh`) that creates two virtual input devices with Android's
  `uinput` tool (a stylus button for Saber, a mouse button for presentation clicks, and Page Up/Down keys for the
  page shortcuts), reads Saber's
  settings file to see its active tool, forwards the pen's presentation keys to the app, and runs
  `penpointer.jar`, which takes the pen's HID mouse exclusively in presentation mode so swipes can be
  remapped (movement is passed on unchanged).

Removing the module undoes all of this.

## Saber

Saber itself is not modified. The integration works from the outside: Android merges the virtual
stylus button into the pen's input (Saber uses the stylus button as its eraser), and the active tool
is read from Saber's settings. Saber's developers aren't involved, so please don't report problems
with this integration to Saber.

## Build

`KS_PASS=<keystore password> tools/build.sh` builds the app with the Gradle wrapper (Kotlin, AndroidX
Preference, Material Components 1.14 with Material 3 Expressive) and packs the module zip into `build/`. It needs JDK 21 and an Android
SDK with platform 35 and build-tools 36 (`ANDROID_HOME`, default `~/Android/Sdk`). The signing key
is created on the first build at `~/tools/stylo2-release.jks` and is not part of the repo.

The settings screen follows the structure of LineageOS's device settings (a preference screen in
`res/xml`, a main switch, categories), using the public AndroidX/Material libraries in place of
SettingsLib, which is only available inside the Android source tree.

## Repository

| Path | Contents |
|---|---|
| `app/` | the Android app (Kotlin) |
| `module/` | KernelSU/Magisk module scripts, including the root helper `stylus-bridge.sh` |
| `docs/` | `PROTOCOL.md` (the pen's BLE protocol), `RESEARCH.md` (findings), `STYLO2.md` (research log) |
| `tools/build.sh` | builds the app and the module zip |
| `tools/penprobe/` | debug app used for the research: subscribes to the pen's notifications and can send a small allowlist of stock commands |
| `tools/gatt_cache_dump.py` | decodes an Android `gatt_cache_*` file into services and characteristics |
| `tools/research/` | test scripts and the tablet's input device list |

## Credits

- Tool and texture icons from [Font Awesome Free](https://fontawesome.com) 6 by Fonticons, Inc.,
  [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/): the same icons
  [Saber](https://github.com/saber-notes/saber) uses for its tools.
- Other icons from [Material Symbols](https://fonts.google.com/icons) by Google, Apache-2.0.
