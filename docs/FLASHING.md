# 🚀 Flashing — Aurora (ESP32-S3)

Everything here was **verified on real hardware on 2026-10-04**: both variants compiled, Wi-Fi build uploaded over COM6, LittleFS image written, plus a real bootloop found → diagnosed → fixed (see ⚠️ below).

---

## 🧰 Prerequisites

| Tool | Version (verified) | Where it lives on the test machine |
|---|---|---|
| ESP32 Arduino core | **3.3.10** | `%LOCALAPPDATA%\Arduino15\packages\esp32` |
| arduino-cli | bundled | `C:\Program Files\Arduino IDE\resources\app\lib\backend\resources\arduino-cli.exe` |
| esptool | 5.3.0 | `...\Arduino15\packages\esp32\tools\esptool_py\5.3.0\esptool.exe` |
| mklittlefs | 4.0.2 | `...\Arduino15\packages\esp32\tools\mklittlefs\4.0.2-db0513a\mklittlefs.exe` |
| ArduinoJson | **7.4.3** (required — code uses the v7 API) | `Documents\Arduino\libraries\ArduinoJson` |
| CH343 USB driver | installed | device shows as `USB-Enhanced-SERIAL CH343 (COMx)` |

**Fresh machine?** Install [Arduino IDE 2.x](https://www.arduino.cc/en/software) → *Boards Manager* → install **esp32 by Espressif** → *Library Manager* → install **ArduinoJson**. Or use standalone `arduino-cli`.

Below, `$CLI` = path to `arduino-cli.exe`, and the **canonical FQBN** is:

```bash
FQBN="esp32:esp32:esp32s3:FlashSize=16M,PartitionScheme=default_8MB"
```

> ⚠️ **Both FQBN options are required — do not drop either one:**
> - `PartitionScheme=default_8MB` → 3.3 MB app partition (the Wi-Fi build is **1.32 MB**, too big for the 1.25 MB default app slot)
> - `FlashSize=16M` → image header must match the real **16 MB** chip. Without it the header says 4 MB and the ROM rejects the partition table: `partition invalid - offset 0x340000 … exceeds flash chip size 0x400000` → **silent bootloop** (verified failure on 2026-10-04, fixed by adding this option)

---

## 1️⃣ Find the port

```bash
$CLI board list
# COM6   serial   Serial Port (USB)   Unknown   ← the CH343 bridge
```

---

## 2️⃣ Compile

```bash
$CLI compile --fqbn "$FQBN" --export-binaries firmware/aurora
# BLE-only variant: add --build-property=build.extra_flags=-D\ AURORA_ENABLE_WIFI=0
```

**Verified results (both variants, this FQBN):**

| Variant | Size | % of 3,342,336 |
|---|---|---|
| Wi-Fi (default) | **1,321,385 B** | 39 % |
| BLE-only | **829,449 B** | 24 % |

- First compile takes a few minutes (toolchain init); afterwards it's cached
- CI runs this exact matrix — see `.github/workflows/firmware.yml`

---

## 3️⃣ Upload

```bash
$CLI upload --port COM6 --fqbn "$FQBN" firmware/aurora
```

**Verified result (Wi-Fi build):**
```
Connected to ESP32-S3 on COM6:  Chip type: ESP32-S3 (QFN56) (revision v0.2)
Flash size configured... 16MB
Wrote 1321536 bytes (850038 compressed) at 0x00010000 in 14.2 seconds
Verifying written data... Hash of data verified.
Hard resetting via RTS pin...
UPLOAD_EXIT=0
```

> ✅ A normal flash **does not touch NVS** — mode settings, schedules, and short-history survive. Only `esptool erase_flash` wipes them.
> ✅ v2 firmware **migrates** settings once from the legacy `ntlwc` namespace to `aurora` on first boot.

> ℹ️ `arduino-cli upload` does **not recompile** — if you changed build flags, run `compile` first (this is how a stale BLE-only build got flashed once — caught via serial banner check).

---

## 4️⃣ Verify the boot (serial)

**Option A — interactive terminal (recommended for humans):**
```bash
$CLI monitor -p COM6 -c baudrate=115200
```
⚠️ This only works in a **real terminal** — in pipes/CI it exits silently with code 0 (verified behavior).

**Option B — scripted capture (works anywhere):**
```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\serial-boot-check.ps1 -Port COM6 -Seconds 16
```
The script pulses the EN line (esptool-style hard reset) and prints whatever the board sends.

**Expected boot log (v2 design — final capture pending):**
```
ESP-ROM:esp32s3-20210327
rst:0x1 (POWERON),boot:0x8 (SPI_FAST_FLASH_BOOT)
entry 0x403c88b8

========================================
  Aurora v2.0.0 — Next-Level LED Wave Controller
  Build: Wi-Fi + BLE
========================================
[1/7] LittleFS mounted                     OK
[2/7] Settings loaded (migrated ntlwc)     OK   ← one-time NVS migration
[3/7] Soft PWM + self-test                 OK - 20kHz on 4 RGB LEDs
[4/7] Short detection (pin 40)             OK
[5/7] 7 modes loaded                       OK
[6/7] BLE advertising as 'Aurora'          OK
[7/7] Web UI: STA → AP fallback            OK
----------------------------------------
SETUP COMPLETE
```

**What to expect physically:** red→green→blue corner sweep on boot (self-test), then the saved mode resumes. BLE shows up in any scanner as **`Aurora`**; Web UI at `http://aurora.local` (STA) or `http://10.10.10.10` (AP fallback).

*(The v1 reference capture — banner `NTLWC ESP32-S3 LED Controller`, `[1/6]…[6/6]` — is preserved in the analysis docs under `old/`.)*

---

## 5️⃣ Upload the Web UI (LittleFS `data/` folder)

**Required for the Wi-Fi build** (firmware upload never touches the `spiffs` partition). Partition layout used (`default_8MB`):

```
Name      Offset      Size
nvs       0x9000      0x5000      ← settings live here (untouched by fs upload)
otadata   0xe000      0x2000
app0      0x10000     0x330000    ← firmware (3.3 MB)
app1      0x340000    0x330000    ← OTA slot
spiffs    0x670000    0x180000    ← LittleFS, 1,572,864 B @ 0x670000
coredump  0x7F0000    0x10000
```

Build the image and flash it (verified commands):

```bash
MKLITTLEFS=".../mklittlefs/4.0.2-db0513a/mklittlefs.exe"
ESPTOOL=".../esptool_py/5.3.0/esptool.exe"

# pack firmware/aurora/data/ into a 1,572,864-byte LittleFS image
"$MKLITTLEFS" -c firmware/aurora/data -p 256 -b 4096 -s 1572864 littlefs.bin

# write it to the spiffs partition (verified: 1,572,864 B → 17,859 compressed)
"$ESPTOOL" --port COM6 write_flash 0x670000 littlefs.bin
```

> The **firmware upload never overwrites the `spiffs` partition** and vice versa — you can update one without the other.

---

## 🧹 Full factory reset (optional)

```bash
"$ESPTOOL" --port COM6 erase_flash
```
Wipes **everything**: firmware, LittleFS history, NVS settings (incl. the migration source). Re-flash afterwards.

---

## ⚠️ Troubleshooting

| Symptom | Fix |
|---|---|
| `partition … exceeds flash chip size 0x400000` bootloop | FQBN missing `FlashSize=16M` → recompile + re-upload with the canonical FQBN above |
| `Sketch too big` (1,321,337 > 1,310,720) | FQBN missing `PartitionScheme=default_8MB` (Wi-Fi build doesn't fit the 1.25 MB default app) |
| Serial totally silent + esptool "No serial data received" | unplug/replug the box's USB (power-cycle); if still dark, hold **BOOT** while plugging in; then retry. USB bridge listed `OK` in Device Manager yet no data = chip-side state, not the driver |
| Monitor exits instantly, no output | you're in a pipe/CI — use Option B (script) |
| `ArduinoJson` compile errors about `JsonDocument` | library is v6 → install **v7** (Library Manager → ArduinoJson → Update) |
| Upload flashed the wrong variant | `upload` reuses the last build — run `compile` with the wanted flags first |
| Board found on wrong COM | unplug other serial gear; CH343 is the only one that answers to esptool |
