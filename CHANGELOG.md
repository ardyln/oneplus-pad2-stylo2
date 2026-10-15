# Changelog

## 0.22
- Actions for attaching the pen to the charger and taking it off (Shortcuts page), e.g. a Home
  Assistant webhook (`"gesture": "pen_attached"` / `"pen_detached"`) or a broadcast.
- "Broadcast every gesture" is now "Send pen events to automation apps", with a description of both
  broadcasts, their extras and how to use them.
- The charger actions can be set back to "Nothing".

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
