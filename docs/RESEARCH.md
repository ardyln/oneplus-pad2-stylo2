# Research notes

How the OnePlus Stylo 2 features were worked out for AOSP ROMs, on a OnePlus Pad 2 (`caihong`) running
PixelOS 17 (Android 17) with KernelSU, October 2026. The protocol summary is in [PROTOCOL.md](PROTOCOL.md);
the earlier passive GATT investigation is the research log in [STYLO2.md](STYLO2.md).

Not included here, on purpose: OnePlus's APK, its decompiled code and the touch HAL binary (copyrighted
by OnePlus; only facts learned from them are written down), copies of AOSP, kernel and Saber sources
(linked instead), device addresses, and screenshots of the stock app or OxygenOS.

## 1. The stock OxygenOS app

- **Package** `com.oplus.ipemanager` ("Smart accessories") 15.0.30, from APKMirror. File SHA-256
  `aa4a6e8b7f6490d19bad6cd13f235c3c50f2bfbaf07a964cea16965a926e7802`, signed by Oplus (certificate SHA-256
  `b0a9bbfc05eee5e7d0a2c97c030586e15bb3301152078f54473bb82df6d8c818`). Decompiled with jadx 1.5.6.
- **What it does:** the background service for both the pen and the keyboard. That covers pen
  pairing and the attach popup, the BLE connection and setup commands, haptics settings, the
  thumb-button broadcast, the settings pages, widgets and device cards, backup/restore, pen
  firmware updates (Goodix DFU) and keyboard handling. Main pen class:
  `com.oplus.ipemanager.btadsorb.ble.PencilBleManager`; service `.btadsorb.CoreService`.
- **SDK for other apps:** `Intent("com.oplus.ipemanager.ACTION.PENCIL_SDK")` bound to
  `com.oplus.ipemanager`, protected by `com.oplus.permission.safe.IOT`. Interface
  `com.oplus.ipemanager.sdk.ISdkAidlInterface` with `setVibrationType(int)`, `startVibration(int)`,
  `startFeedBackVibration()`, `stopFeedBackVibration()`, `getVibrationSwitchState()`,
  `isFunctionVibrationEnable()`, and callbacks such as `onVibrationSwitchStateChange`. Only OnePlus
  Notes and approved apps can use it, which is why OxygenOS haptics are limited to them.
- **Thumb button:** a notification on `180938ac-…` makes the app broadcast
  `com.oplus.ipemanager.action.PENCIL_DOUBLE_CLICK` (permission
  `com.oplus.ipemanager.permission.receiver.DOUBLE_CLICK`). Apps act on it according to
  `Settings.Global ipe_pencil_double_click`.
- **Settings it keeps:** `settings_enable_oppo_pencil` (global), shared prefs
  `sp_key_unic_write_vibration_type` (0–3) and `sp_key_unic_written_feedback_level` (0–4, default 2;
  the settings slider has `setMax(4)`).
- **Commands** (written to `18093046-…` as `2c <cmd> <len> <payload>`):

  | Bytes | Stock name |
  |---|---|
  | `2c 08 01 01` | queryBatteryLevel |
  | `2c 06 01 05` | queryFwVersion |
  | `2c 25 01 03` | queryHwVersion |
  | `2c 26 01 0b` / `0d` | queryPencilType |
  | `2c 53 01` | querySerialNum. Never sent in this project |
  | `2c 38 01 01` / `2c 39 01 01` | magnet in / out |
  | `2c 90 01 01 01` / `00` | pencil enabled / disabled |
  | `2c 92 01 02 ff 02` | startVibration (one short vibration) |
  | `2c 94 02 <type> <level+1>` | writing feedback type and level; `<type> 00` = off (`setWrittenFeedbackState`) |
  | `2c 91 01 <0/1>`, `2c 93 01 <0/1>` | presentation mode off/on, and its pointer mode (see section 6) |

- `a6ed0401-…` is the **Goodix DFU** (firmware update) service (`com.goodix.ble.gr.lib.dfu.v2.DfuProfile`).
  This project never writes to it.

## 2. Touch panel side (turned out not to matter on the Pad 2)

- Stock's `startFeedBackVibration()` / `stopFeedBackVibration()` call
  `OplusTouchNodeManager.writeNodeFileByDevice(0, 38, "3" / "2")`.
- **Node table** in the touch HAL (`/odm/bin/hw/vendor-oplus-hardware-touch-V2-service`): an array of
  92-byte structs in `.data`, each `{int id; int flag; char dir[40]; char name[…]}`. Checked against the
  IDs LineageOS uses (1 double_tap_enable, 21 double_tap_enable_indep, 26 game_switch_enable,
  182 report_rate, 192 glove_mode_enable). Pen nodes: **32 `pencil_connected`, 38 `pencil_control`,
  166 `pencil_healthinfo`, 170 `pencil_opp`**, all under `/proc/touchpanel/`.
- Kernel (LineageOS `android_kernel_oneplus_sm8650-modules`, `oplus_touchscreen_v2`):
  `proc_pencil_control_write` → `mode_switch(MODE_PEN_CTL, value)`, ignored while suspended. In the
  NT36532 driver, `nvt_enable_pen_vibrator` sends `EVENTBUFFER_EXT_PEN_VIBRATOR_ON` for 3 and `…_OFF` for 2.
- **Result:** writing 3 or 2 there had no effect we could feel. The pen produces the texture by
  itself once it has received `2c 94`.

## 3. On-device experiments (all writes were stock commands)

| Test | Result |
|---|---|
| `pencil_control` = 3 only | no vibration (pen hadn't received `2c 94` yet) |
| + `2c 90 01 01 01`, `2c 94 02 00 03` | texture while writing, in any app |
| screen off/on, magnet, pen sleep | texture kept |
| `2c 90 01 01 00` | texture kept, so `2c 90` is not the switch |
| Bluetooth off/on (pen reconnects) | texture gone; the pen forgets it on every disconnect |
| only `2c 94 02 00 03` after reconnecting | texture back, so this is the only command needed |
| types 0–3 at level 2 | pencil clearest; eraser subtler; ballpoint subtler still; pen subtle but steady |
| level `01` / `05` | barely noticeable / strongest (later, in everyday use, `01` couldn't be felt at all) |
| `pencil_control` = 2 with texture set, and after a reconnect | still vibrates: the node isn't needed |
| `2c 94 02 00 00` | vibration off |
| `2c 92 01 02 ff 02` | one buzz, no screen contact needed |

Other observations:
- `1809396a` reports **`20` on the magnetic charger, `21` off it**; `2c 90 01 01 xx` follows it (`00` / `01`).
- `1809314a` (readable) is the battery percentage. `18093212` reads `21` both on and off the magnet,
  so its meaning is unknown.
- The pen's Nordic UART service (`6e400003`) is a debug console: `sleep enter/exit`, `film 24` per thumb
  touch, `film int low`, `chg evt …`, and `rx …` statistics every 5 s while awake.

## 4. The thumb button

- **The firmware detects double taps and reports only those:** one `01` on `180938ac` per double tap.
  Single taps, holds and slides send nothing; double taps can follow each other about 0.4 s apart.
  This matches the stock naming (`UUID_DOUBLE_CLICK_NOTIFY_CHARACTER`, `PENCIL_DOUBLE_CLICK`).
- **The button only notifies while other notifications are subscribed too.** Subscribing to
  `180938ac` alone gave nothing; with the stock set (`18093046`, `180938ac`, `180937bc`, `1809396a`)
  it works.
- It does not wake a sleeping pen.

## 5. Android input: a virtual stylus button

Goal: make Saber (or any app) see a stylus button press without changing the app.

- `touchpanel_pen` (event8) declares `BTN_DIGI BTN_TOUCH BTN_STYLUS BTN_STYLUS2`, `ABS_X/Y` (0–21199 /
  0–29999), `ABS_PRESSURE` (0–16383), `ABS_DISTANCE` (0–1), `ABS_TILT_X/Y` (±60), `INPUT_PROP_DIRECT`.
- **Injecting into that device doesn't work:** the driver (`touchpanel_common_driver.c`, around line
  1318) reports `BTN_STYLUS` with every pen frame (`input_report_key(…, BTN_STYLUS, pen_info.btn1)`),
  so an injected press is reset the moment the pen hovers.
- **External stylus fusion** (AOSP `frameworks/native/services/inputflinger`): a device whose only
  inputs are stylus buttons is classified as `EXTERNAL_STYLUS` (`EventHub.cpp`), and
  `TouchInputMapper::applyExternalStylusButtonState` merges its buttons into the touchscreen's stylus
  pointer. Without a pressure axis, `assignExternalStylusId` skips pointer fusion ("Stylus does not
  support pressure, no pointer fusion needed"), so the pen's own pressure, tilt and timing are untouched.
  A tool type (eraser) is only passed on by devices that also report pressure (`applyExternalStylusTouchState`;
  `assignExternalStylusId` starts pointer fusion only when the external pressure is above 0).
- **Creating the device:** Android's own `/system/bin/uinput` tool (`uinput -` reads JSON commands from
  stdin). Registering `{"type":"UI_SET_EVBIT","data":[1]}`, `{"type":"UI_SET_KEYBIT","data":[331,332]}`
  gives `classes=KEYBOARD | EXTERNAL_STYLUS | EXTERNAL`; injecting `[1,331,1,0,0,0]` presses the button.
  When the stdin stream closes, the tool exits and the device disappears.
- Verified with Saber: while the virtual button was held, hovering switched Saber to the eraser and
  every stroke erased.
- Other apps with the primary button held: Notewise and Xodo react to it (their own stylus button
  handling); Google Keep and Xournal++ Mobile ignore it.
- **Eraser end, tried and dropped:** a test device with `BTN_TOOL_RUBBER` (and, in a second try,
  `ABS_PRESSURE` held at its maximum so Android fuses the pointer) did not make Google Keep erase. With
  pressure, the fused pen would also lose its own pressure, and while such a device reports zero
  pressure every touch waits up to 72 ms (`EXTERNAL_STYLUS_DATA_TIMEOUT`) for stylus data, so this
  route isn't used.

## 6. Presentation mode

- Stock: the "Presentation mode" switch calls `setLaserFunctionAndResetMode`, which sends
  `2c 91 01 01`/`00` and `2c 93 01 00`. The mode is later switched by the OxygenOS system through
  `ISystemAidlInterface.setLaserMode`, gated by the system feature `oplus.software.laser.support`.
- Tested: with `2c 91 01 01`, the flat surface near the tip becomes a touch area. A finger on it holds
  `KEY_F19` (a tap gave a 60–90 ms press in the first test; later 30–420 ms), swipes send mouse wheel steps (Google Photos zoomed), and the
  pen moves the mouse cursor with its motion sensor. In mode 0 the cursor only moves while the surface
  is touched (1,464 of 1,464 movement events during an F19 hold); in mode 1 it moves all the time
  (4,279 of 5,729 outside a hold). A short `KEY_F20` press comes with a double tap on the touch area, together with the usual
  `180938ac` double-tap notification (tested later), so it adds no gesture of its own.
- Nothing extra is sent on the vendor characteristics; the console prints `film FF` / `film 05`
  (swipes), `film 06` / `film 08` (hold start and end).
- **Tap timing,** measured with the app's log: taps on the touch area lasted 30–420 ms, deliberate holds
  600 ms and more. Double taps on the touch area (20 logged, in both pointer modes) each gave `KEY_F20`
  and the `180938ac` notification within 0–5 ms of each other, in either order, and **never any
  `KEY_F19`**: the pen recognises them itself. So single taps run at once by default; a delay can
  still be set as a safeguard.
- **Shortest tap:** with ten very quick taps three seconds apart, only six arrived, held 45–75 ms.
  All F19 durations seen so far are multiples of about 15 ms (30, 45, 60, 75, 90 ms), so the pen
  seems to scan the touch area every ~15 ms and report a touch after two or three scans; quicker
  flicks are dropped in the firmware. Very quick taps less than about half a second apart are
  reported as double taps (`KEY_F20`) instead.
- Android's developer option "Show key presses" seemed to show `KEY_F19` only after the finger was
  lifted. A raw recording (`getevent -lt` on the pen's Keyboard device) showed the press arriving at
  touch time and the release at lift time (for example 993 ms apart), so the overlay just isn't a
  timing reference.

## 7. Taking over the pen's mouse

Goal: let the user remap the swipes (e.g. to next/previous page) instead of Android scrolling.

- `EVIOCGRAB` (`0x40044590`) on the pen's "OnePlus Stylo 2 Mouse" event device gives one process all of
  its events; Android then ignores the pen's mouse (tested: Android stayed still while the test caught
  1,183 events). The kernel ends the grab when the process exits, so a crash can't leave the pen dead.
- Java in a root `app_process` can call it through the hidden `android.system.Os.ioctlInt` (reflection).
  Calling `available()` on the event device's stream killed the process; blocking reads work.
- The helper (`helper/`, `penpointer.jar`) grabs while the app's `files/pen_grab` contains `1`, sends
  `REL_X`/`REL_Y` and buttons on through its own `uinput` device ("Stylo 2 pointer"), and forwards each
  wheel step to the app as `REL_WHEEL UP`/`DOWN`. The app then scrolls (its virtual mouse has
  `REL_WHEEL` and `REL_WHEEL_HI_RES`), sends Page Up/Down, clicks, or runs any shortcut action.
- The helper also reads the pen's "Keyboard" device (not grabbed) for `KEY_F19`/`KEY_F20`. This used
  to be `getevent -l` in the shell script, but **`getevent` doesn't exit when its device goes away**:
  after a Bluetooth reconnect it kept reading the deleted node and no touch-area keys reached the app.
  In Java a read on a removed evdev node fails, so the helper looks the device up again by name.
- Up to v0.21 the helper crashed on the first forwarded movement: it used
  `ProcessBuilder.Redirect.DISCARD` (Java 9), which Android's libcore doesn't have, and it had been
  compiled against the JDK. It's now compiled against `android.jar` as the boot classpath (no
  lambdas, since `android.jar` lacks `LambdaMetafactory`), so such calls fail the build.
- **Clicks at the pointer:** a held left mouse button is not a long press for most apps; the right
  button is (Android's "context click" opens the same menus). A real touch at the pointer isn't an
  option either: Android 17's `dumpsys input` doesn't show the mouse pointer's position.

## 8. Saber

- Package `com.adilhanney.saber` (GPL-3.0). Saber doesn't use any OnePlus API.
- Tools are `ToolId` (`packages/sbn/lib/tool_id.dart`): 0 highlighter, 1 fountain pen, 2 ballpoint pen,
  3 pencil, 4 shape pen, 5 eraser, 6 select, 7 text editing, 8 laser pointer. The toolbar's first button
  is the current pen; tapping it again opens a menu with fountain pen, ballpoint pen and shape pen.
- The active tool is saved on every change as `flutter.lastTool` (the index, as a `long`) in
  `shared_prefs/FlutterSharedPreferences.xml`. The default (fountain pen) isn't stored. The file updates
  within a fraction of a second.
- Stylus button semantics (`editor.dart`, `canvas_gesture_detector.dart`): a button press while hovering
  switches to the eraser; after an eraser stroke it switches back to the previous tool. While the button
  is held, the next hover switches to the eraser again, so the toolbar flickers between strokes.
- Saber's [contributing rules](https://github.com/saber-notes/saber/blob/main/.github/CONTRIBUTING.md)
  don't accept AI-generated code, so this project integrates from the outside instead of patching Saber.

## 9. Which app is in front

- In desktop windowing mode several apps stay resumed, so `UsageEvents.ACTIVITY_RESUMED` misses focus
  changes between visible windows.
- An accessibility service listening to `typeWindowsChanged|typeWindowStateChanged` with
  `flagRetrieveInteractiveWindows` gives the focused `TYPE_APPLICATION` window; only its root package
  name is read.
- After a reboot, nothing can run until the first unlock (credential-encrypted storage:
  `sys.user.0.ce_available`), so the module's `service.sh` waits for it.

## 10. Other devices

Could the Bluetooth-only features (shortcuts, presentation mode, battery) work on another rooted
Android device? Tested with a rooted Xiaomi phone (Android 17, KernelSU), with the tablet's Bluetooth
turned off so the pen was free:

- **Pairing from Bluetooth settings:** the pen never showed up, and the phone's scan never saw its
  name or address.
- **Connecting directly by address** (penprobe with `--ez random true`: the pen's address is a random
  static one): the connection attempt timed out after 30 s (`status=147`, `GATT_CONNECTION_TIMEOUT`),
  so nothing the phone could connect to was received from that address.

So the pen only talks to the tablet it was paired with through the magnetic charger (where the
tablet learns its address from the kernel's `pencil_addr` event). The app itself has nothing specific
to the Pad 2 apart from the install check, but without a way to pair the pen elsewhere, the module
stays Pad 2 only.

## 11. Installing the app from the boot script

KernelSU without a mounting metamodule doesn't overlay `system/`, so `service.sh` installs the app with
`pm install`. `pm` hands its stdin and output to `system_server`, which matters:

- **A pipe** (`out=$(pm install …)`) stays open on the `system_server` side while an install waits,
  so `$(…)` never returned and the boot script hung (seen after removing and reinstalling the module).
- **A file under `/data/adb`** is refused: the call fails at once with `Failed transaction`
  (`system_server` may not use those file descriptors). The same applies to `settings`, which goes
  through the same path.

So `pm` reads from `/dev/null` and writes to a file in `/data/local/tmp`, with a 2-minute limit, and
the app is only recorded as installed when the output says `Success`; otherwise the next boot tries
again. Tested by removing and reinstalling both Pad 2 modules and rebooting: both apps installed at
boot.

## Tools

`tools/research/` has the scripts used for the tests above (they need adb and root). The pen's address is
read from a local file and never written into them.
