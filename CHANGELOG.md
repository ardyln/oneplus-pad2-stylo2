# Changelog

## 0.25
- **New:** the pen vibrates when a double tap toggles Saber's eraser, so you feel the switch
  between the eraser and your tool. The same goes for the stylus button in the other apps. Both are
  on by default; turn them off with **Vibrate when the eraser toggles** on the Saber page and
  **Vibrate when the stylus button toggles** on the Other apps page. "Vibrate on each double tap"
  still covers every app as before, and the pen never vibrates twice for one double tap.

## 0.24
- **Fix:** with both Pad 2 modules installed at once, only one app got installed. Android may ask
  you to allow each app's install but shows one such prompt at a time, so the second install waited
  for good. The two modules now take turns, each install gets 5 minutes, and the time limit now
  stops the install itself. Whether the app is installed is checked by comparing the installed APK
  with the module's, so an install you allow late is recognised at the next boot.

## 0.23
- **Fix:** after installing the module (e.g. removing and reinstalling it), the boot script could
  hang on the app install, and the app was never updated. `pm` hands its input and output to
  Android's system service: a pipe stays open while an install waits, so the script waited for good,
  and a file under `/data/adb` is refused ("Failed transaction"). pm now reads from `/dev/null` and
  writes to a file in `/data/local/tmp`, with a 2-minute limit, and the app is only recorded as
  installed when it worked, so the next boot tries again otherwise.

## 0.22

Fixes:
- **Air mouse:** the presentation pointer helper crashed on the first pen movement (it used a Java
  API that Android doesn't have) and restarted every few seconds, so the air mouse didn't work
  since 0.15. The helper is now compiled against Android's API, so this kind of error fails the build.
- **After a reconnect** (e.g. Bluetooth off and on), taps and double taps on the touch area no longer
  reached the app: the helper's `getevent` kept reading the removed device. The Java helper now
  forwards F19/F20 itself and finds the pen's keyboard again after a reconnect.
- Webhooks and broadcasts from presentation gestures carry the gesture's name (e.g.
  `presentation_tap`) instead of `double_tap`; a touch-area release without a press is ignored.

New:
- **Charger actions:** run any action when the pen is attached to the charger or taken off
  (Shortcuts page), e.g. a Home Assistant webhook (`"gesture": "pen_attached"` / `"pen_detached"`).
- **"Send pen events to automation apps"** (formerly "Broadcast every gesture") describes both
  broadcasts (`DOUBLE_TAP`, `CHARGER`), their extras and how to use them.
- **"Nothing"** for double-tap shortcuts (to skip a count, e.g. an action only on four double taps)
  and for the charger actions.
- **Right click** (opens menus, like a long press) and **middle click** as presentation gesture
  actions. A held mouse button isn't a long press for most apps, so there's no long-press action.
- **Touch timeline** on the presentation page: each touch's duration against the zones (too short,
  tap, hold), the short end against the pen's assumed 15 ms scans (an estimate from the measured
  durations), when a single tap runs after letting go, and the last 15 seconds of taps, holds,
  double taps and swipes. The longest tap and the single-tap delay are draggable knobs on it.
- The live card shows the key the pen sent (F19, F20, wheel), how long it was held, what it counted
  as, the action, and whether it ran.
- Single taps on the touch area run **instantly** by default: tests showed the pen reports double
  taps on its own, so "Delay single taps" is only a safeguard now.
- App lists (shortcut "Open an app", "Add app" on the Other apps page) have a search field and icons.

## 0.21
- Strength 0 is described correctly: the pen accepts it, but it can't be felt.
- Other apps: the default texture is pencil at strength 4 (the strength used to follow the main one).

## 0.20
- "Stylus button apps" is now **Other apps**: empty by default, without Saber, and it says plainly
  that a double tap holds Android's primary stylus button (`BUTTON_STYLUS_PRIMARY`).
- Saber's double-tap eraser has its own switch on the Saber page (existing settings are migrated).
- Optional single texture and strength for the other apps (0.19).
- Presets: save all settings under a name, apply them from the app or with a shortcut (0.18).
- Presentation mode tap timing: adjustable longest tap and wait for a second tap, explained in plain
  words with a live readout of each touch (0.16, 0.17).
- Assignable presentation gestures (swipes, single tap, double tap: scroll with a strength, click,
  next/previous page or any shortcut action) through a root helper that takes the pen's mouse
  exclusively; long-press a Quick Settings tile to open its page; reset to defaults on every page and
  for everything (0.15).
- Module updates through KernelSU (`updateJson`; Magisk untested).

## 0.14
- Next/previous page shortcuts (Page Down/Up) and Quick Settings tiles for presentation mode, the
  writing feel and the texture.

## 0.13
- Clearer UI on every page; the active-app detection turns itself back on after updates.

## 0.12
- Presentation mode pauses in apps that use the stylus button.

## 0.11
- Option to keep presentation mode on when the pen is attached.

## 0.10
- Vibration for presentation scrolling and taps.

## 0.9
- The app's own settings as shortcut actions.

## 0.8
- Material 3 Expressive design; presentation mode with clicks.

## 0.7
- Saber textures independent of the main switch; live event log.

## 0.6
- Saber integration (eraser on double tap, texture per tool) without modifying Saber.

## 0.5
- Thumb-button shortcuts with chained double taps.

## 0.2
- Kotlin and Gradle rewrite; settings screen in the LineageOS style.

## 0.1
- Writing feedback, thumb button and battery level.
