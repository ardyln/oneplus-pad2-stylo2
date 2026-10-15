# Changelog

## 0.22
- **Fix:** the presentation pointer helper crashed on the first pen movement (it used a Java API that
  Android doesn't have) and restarted every few seconds, so the air mouse didn't work since 0.15. The
  helper is now compiled against Android's API, so this kind of error fails the build.
- A release of the touch area without a press is ignored (it was measured from boot).
- **Fix:** after the pen reconnected (e.g. Bluetooth off and on), taps and double taps on the touch
  area no longer reached the app: the helper's `getevent` kept reading the removed device. The Java
  helper now forwards F19/F20 itself and reopens the pen's keyboard after a reconnect.
- Webhooks and broadcasts from presentation gestures carry the gesture's name (e.g.
  `presentation_tap`) instead of `double_tap`.
- New actions for presentation gestures: right click (opens menus, like a long press) and middle
  click, at the pointer. (A held mouse button isn't a long press for most apps, so there's no
  separate long-press action.)
- The live card on the presentation page shows the key the pen sent (F19, F20, wheel), how long it
  was held, what it counted as, the action, and whether it ran.
- Touch timeline on the presentation page: each touch's duration against the zones (too short, tap,
  hold), when a single tap runs after letting go, and the last 15 seconds of taps, holds, double
  taps and swipes. The longest tap and the single-tap delay are handles on it, replacing the two
  sliders.
- Actions for attaching the pen to the charger and taking it off (Shortcuts page), e.g. a Home
  Assistant webhook (`"gesture": "pen_attached"` / `"pen_detached"`) or a broadcast.
- "Broadcast every gesture" is now "Send pen events to automation apps", with a description of both
  broadcasts, their extras and how to use them.
- The charger actions can be set back to "Nothing", and double-tap shortcuts can be "Nothing" too, to
  skip a count (e.g. an action only on four double taps).
- App lists (shortcut "Open an app", "Add app" on the Other apps page) have a search field and show
  app icons.
- Presentation mode: single taps on the touch area run instantly by default. Tests showed the pen
  reports double taps on its own (never as two taps), so "Delay single taps" (formerly "Wait for a
  second tap") is only a safeguard now, and its explanations say so.

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
