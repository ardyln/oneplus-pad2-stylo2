# OnePlus Stylo 2 Bluetooth LE protocol

Everything known about the pen's GATT interface, from the tablet's GATT cache, the stock OxygenOS app
(`com.oplus.ipemanager` 15.0.30, see [RESEARCH.md](RESEARCH.md)) and tests on a OnePlus Pad 2 running
PixelOS 17. "Tested" means verified on the device; "stock" means taken from the stock app only.

**Safety:** only write the commands listed as safe below. Never write to the firmware update services
(`a6ed0401-…`, `58480000-…`), never send `2c 53` (serial number), and don't read the Device Information
serial number. The pen's Bluetooth address is a device identifier; keep it out of logs and repos.

## Identity

- Bluetooth name **"OnePlus Stylo 2"**, LE only, bonded like a regular HID device.
- USB-style IDs (HID): vendor `0x22D9` (8921), product `0x386A` (14442).
- On the tablet it shows up as two input devices: "OnePlus Stylo 2 Mouse" (`BTN_MOUSE BTN_RIGHT BTN_MIDDLE`,
  `REL_X REL_Y REL_WHEEL`) and "OnePlus Stylo 2 Keyboard". The tip itself is read by the touch screen
  (`touchpanel_pen`), not over Bluetooth.

## GATT table

Handles are from the cache on the test device; they can differ with other firmware.

| Handles | Service | Characteristic | Properties | Meaning |
|---|---|---|---|---|
| 0x0001–0x000b | `1800` Generic Access | `2a00` Device Name | read | |
| | | `2a01` Appearance | read | |
| | | `2a04` Peripheral Preferred Connection Parameters | read | |
| | | `2aa6` Central Address Resolution | read | |
| | | `2ac9` Resolvable Private Address Only | read | |
| 0x000c–0x000f | `1801` Generic Attribute | `2a05` Service Changed | indicate | |
| 0x0010–0x0018 | `180a` Device Information | `2a25` Serial Number | read | **don't read** |
| | | `2a26` Firmware Revision | read | |
| | | `2a29` Manufacturer Name | read | |
| | | `2a50` PnP ID | read | |
| 0x0019–0x001c | `180f` Battery | `2a19` Battery Level | read, notify | standard battery |
| 0x001d–0x0032 | `1812` HID | `2a4e` Protocol Mode | read, write-no-rsp | |
| | | `2a4d` Report (×2) | read, write, notify | mouse and keyboard input reports |
| | | `2a4b` Report Map | read | |
| | | `2a22` Boot Keyboard Input | read, write, notify | |
| | | `2a32` Boot Keyboard Output | read, write-no-rsp, write | |
| | | `2a4a` HID Information | read | |
| | | `2a4c` HID Control Point | write-no-rsp | |
| 0x0033–0x003b | `a6ed0401-d344-460a-8075-b9e8ec90d71b` **Goodix DFU** | `a6ed0402` | notify | firmware update. **Never write** |
| | | `a6ed0403` | write-no-rsp | 〃 |
| | | `a6ed0404` | write-no-rsp, indicate | 〃 |
| 0x003c–0x0043 | `18092dbc-2a69-11ec-8d3d-0242ac130003` **Command** | **`18093046`** | write-no-rsp, write, notify | commands and their replies (below) |
| | | **`1809314a`** | read | battery percent (tested: `64` = 100 %) |
| | | `18093212` | read | unknown; read `20`/`21`, not the charger state (tested) |
| 0x0044–0x0050 | `18093398-2a69-11ec-8d3d-0242ac130003` **Status** | **`180937bc`** | notify | battery percent, on change (stock: "battery change") |
| | | **`180938ac`** | notify | thumb button: `01` per **double tap** (stock: "double click") |
| | | **`1809396a`** | notify | charging state: `20` charging (on the magnet), `21` not (tested; stock: `onChargingState(value == 0x20)`) |
| | | `180932da` | notify | log code, 2 bytes (stock: "log notify", logged as `r=%02d%03d`) |
| 0x0051–0x0059 | `6e400001-b5a3-f393-e0a9-e50e24dcca9e` Nordic UART | `6e400002` | write-no-rsp, write | debug console input. **Don't write** |
| | | `6e400003` | notify | debug console output (text, below) |
| | | `6e400004` | notify | not seen |
| 0x005a–0x005d | `58480000-5a6a-1122-3300-004095112200` | `58480001` | write-no-rsp, write, notify | unknown, possibly firmware update. **Don't write** |

All characteristics with "UUID 18093…" share the base `-2a69-11ec-8d3d-0242ac130003`.

### Notifications need company

The pen only sends thumb-button notifications while other notifications on the connection are enabled
too. Subscribing to `180938ac` alone gives nothing; the stock set (`18093046`, `180938ac`, `180937bc`,
`1809396a`) works (tested). Subscriptions (CCC descriptors) are shared by every app on the connection,
so one app turning them off affects the others.

## Commands

Written to `18093046`, normally with write-with-response. Format `2c <command> <length> <payload…>`.
Replies come as notifications on `18093046` and start with the same first 3–4 bytes.

| Command | Payload | Effect | Reply | Status |
|---|---|---|---|---|
| `2c 01 01 01` | | query charging state | `2c 01 01 01 <s>`: 1 charging, 0 not | stock |
| `2c 06 01 05` | | query firmware version | `2c 06 01 05 <5 bytes>`: version, bytes 4–8 | stock |
| `2c 08 01 01` | | query battery | `2c 08 01 01 <percent>` | stock |
| `2c 25 01 03` | | query hardware version | `2c 25 01 03 <3 ASCII bytes>` | stock |
| `2c 26 01 0b` / `2c 26 01 0d` | | query pencil type | `2c 26 01 <n> <n ASCII bytes>` | stock |
| `2c 38 01 01` | | tablet tells the pen: on the magnet | | stock |
| `2c 39 01 01` | | tablet tells the pen: off the magnet | | stock |
| `2c 53 01` | | query serial number | `2c 53 01 <n> <n ASCII bytes>` | **never send** |
| `2c 90 01 01 <s>` | 1 / 0 | pencil enabled / disabled | | tested (accepted; no effect on the texture) |
| `2c 91 01 <s>` | 1 / 0 | presentation mode ("laser function") on / off | | stock, untested |
| `2c 92 01 02 ff 02` | | one short vibration | | **tested** |
| `2c 93 01 <m>` | 0 / 1 | presentation ("laser") mode | | stock, untested |
| `2c 94 02 <type> <level>` | type 0–3, level 0–5 | writing feedback | | **tested** |

**Writing feedback** (`2c 94`):
- `<type>`: `00` pencil, `01` eraser, `02` ballpoint pen, `03` pen.
- `<level>`: `01`–`05` = strength 0–4 (the stock slider), `00` = off. Stock defaults: type 0, strength 2,
  feedback off.
- The pen produces the texture itself while the tip touches the screen; nothing else has to be sent.
- **The pen forgets it on every Bluetooth disconnect**; resend after each connection. It's kept while the
  pen sleeps, sits on the magnet, or the screen is off (tested).

**Presentation mode** (`2c 91`, `2c 93`): the stock settings describe it as "Swipe along the flat
surface near the tip of the stylus to turn pages" and "Press and hold on the flat surface near the tip
of the stylus to activate the virtual laser pointer". Those events most likely arrive through the pen's
HID mouse and keyboard reports. The meaning of the two mode values is unknown. Stock only sends these
after the feedback setting on connect, according to its settings `ipe_laser_mode` and a "demo mode" flag.

## Messages from the pen on `18093046`

| Bytes | Meaning |
|---|---|
| `2c 90 01 01 01` | pencil status "active": sent when the pen leaves the magnet (tested). Stock sets `settings_enable_oppo_pencil = 1` |
| `2c 90 01 01 00` | pencil status "idle": sent when it goes on the magnet (tested). Stock sets the setting to 0 and answers `2c 90 01 01 00` |
| replies | see the command table |

Stock treats any reply it doesn't recognise as a charging-state reply (byte 4: 1 = charging).

## What stock does after connecting

In order, about 300 ms apart: query battery, firmware version, pencil type, serial number, hardware
version and charging state; send the magnet state (`2c 38`/`2c 39`); enable battery notifications;
then send the writing feedback setting (`2c 94`) and the presentation-mode settings (`2c 91`, `2c 93`).
This module only sends `2c 94` (and reads `1809314a`), which is enough.

## Debug console (Nordic UART `6e400003`)

Plain ASCII lines while the pen is awake:

| Line | Meaning |
|---|---|
| `sleep enter` / `sleep exit` | the pen goes to sleep after about a minute without movement; moving or hovering wakes it (thumb touches don't) |
| `film 24` | a thumb-button touch registered |
| `film int low` | seen during button tests, unclear |
| `chg evt <3 bytes>` | charging event |
| `rx <8 hex> <numbers…>` | statistics, every 5 s |
| `c:<n> v:<n> t:<n> i:<n>` | seen once when going on the charger (charge values) |

## Not used by this pen

LineageOS's `org.lineageos.pen` looks for a pressure relay service `0783b03e-…` (used by other OnePlus
pens). The Stylo 2 doesn't have it: pressure, tilt and hover come from the touch screen.
