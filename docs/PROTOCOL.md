# OnePlus Stylo 2 BLE protocol (as used by this module)

Observed on a OnePlus Pad 2 (`caihong`) running PixelOS 17. The research notes, including how this was
found, are in `docs/STYLO2.md` of the [keyboard module repo](https://github.com/ardyln/oneplus-pad2-keyboard-ble).

## Characteristics used

| UUID | Service | Use |
|---|---|---|
| `18093046-2a69-11ec-8d3d-0242ac130003` | `18092dbc-…` | commands (write), replies (notify) |
| `1809314a-2a69-11ec-8d3d-0242ac130003` | `18092dbc-…` | battery percent (read) |
| `18093212-2a69-11ec-8d3d-0242ac130003` | `18092dbc-…` | charging state (read): `20` no, `21` yes |
| `180938ac-2a69-11ec-8d3d-0242ac130003` | `18093398-…` | thumb button (notify): `01` per touch |
| `180937bc-2a69-11ec-8d3d-0242ac130003` | `18093398-…` | battery percent (notify, on change) |
| `1809396a-2a69-11ec-8d3d-0242ac130003` | `18093398-…` | charging state (notify, on change) |

The pen only sends thumb-button notifications while other notifications on the connection are enabled
too. This module enables the command channel, button, battery and charging notifications.

## Commands

Written to `18093046` as `2c <command> <length> <payload>`.

| Bytes | Effect |
|---|---|
| `2c 94 02 <texture> <level>` | Writing feedback. Texture: `00` pencil, `01` eraser, `02` ballpoint pen, `03` pen. Level: `01`–`05` (weak to strong), `00` = off |
| `2c 92 01 02 ff 02` | One short vibration |

The pen forgets the writing feedback setting whenever its Bluetooth connection drops, so it has to be
sent again after every connection. It is kept while the pen sleeps, while it sits on the magnet, and
while the tablet's screen is off.

## Never send

The pen also has a firmware-update service (`a6ed0401-…`, Goodix DFU) and another unknown write channel
(`58480001-…`). This module never writes to them.
