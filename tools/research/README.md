# Research scripts

Used for the experiments in [docs/RESEARCH.md](../../docs/RESEARCH.md). They need adb and root on the tablet.

| File | What it does |
|---|---|
| `send.sh cmd[,cmd...]` | Restarts penprobe (`tools/penprobe`) and sends its allowlisted stock commands (`texture:T:L`, `texture-off`, `oneshot`, …). Reads the pen's address from `$PEN_MAC_FILE` (default `~/tools/stylo2-re/pen.mac`), which is never committed |
| `btn.sh` | Prints thumb-button notifications and pen console lines since the last call, then clears the log |
| `stylus-btn-test.json` | Input for Android's `uinput` tool: registers a stylus-button-only device, presses the button for 25 s, releases it |
| `stylus-btn-run.sh` | Runs that test on the tablet, vibrates the tablet at the start and end of the pressed phase, and prints Saber's `lastTool` changes |
| `getevent-lp-caihong.txt` | `getevent -lp` of the Pad 2 with the pen and keyboard connected: input devices and their capabilities |
