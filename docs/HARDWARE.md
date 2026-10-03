# 🔩 Hardware — Aurora LED Box

Complete hardware reference: the board, the LEDs, every pin, and the protection circuit.

---

## 🧠 Main controller

| Item | Value |
|---|---|
| **Chip** | **ESP32-S3** (QFN56, revision v0.2 as probed) |
| **Cores** | Dual-core Xtensa LX7 @ 240 MHz + LP core |
| **Radios** | Wi-Fi + Bluetooth 5 (LE) |
| **PSRAM** | 8 MB embedded (AP_3v3) — *present but unused by the firmware* |
| **Flash** | **16 MB** (probed via `esptool flash_id` — manufacturer 0x5e, device 0x4018) |
| **USB** | **CH343 USB-UART bridge** (VID `1A86` PID `55D3`) → shows up as a COM port (e.g. `COM6`) |
| **Example MAC** | `cc:ba:97:23:d1:30` (per-device, printed by esptool) |

The CH343 bridge handles both flashing and serial logging — one USB-C cable is enough.

---

## 💡 The LEDs — 4 × RGB (not a strip!)

Aurora drives **4 discrete RGB LEDs**, one in each corner of the box. Each LED has its own R, G, B line → **12 GPIO outputs**, all driven by **software PWM at 20 kHz** (hardware timer interrupt).

> ℹ️ The old docs called this an "addressable LED strip (WS2812B)" — that was wrong. There is no data line and no LED chipset; it's plain PWM dimming.

### Pin map (firmware ground truth)

| LED | Corner | R pin | G pin | B pin |
|---|---|---|---|---|
| **LED 0** | Top-Right (TR) | 11 | 13 | 14 |
| **LED 1** | Top-Left (TL) | 4 | 5 | 6 |
| **LED 2** | Bottom-Left (BL) | 7 | 15 | 16 |
| **LED 3** | Bottom-Right (BR) | 8 | 9 | 10 |

### Corner ↔ LED index mapping

App/Web UI use **corner indices 0–3**; the firmware maps them to physical LEDs:

| Corner index | Name | Physical LED |
|---|---|---|
| 0 | TL (Top-Left) | LED 1 |
| 1 | TR (Top-Right) | LED 0 |
| 2 | BL (Bottom-Left) | LED 2 |
| 3 | BR (Bottom-Right) | LED 3 |

> ⚠️ In the old code this mapping is **re-declared in three places** (`SolidMode`, `MODE_5`, `MODE_6`, startup animation). The rebuild keeps a **single source of truth** in `Config.h`.

### PWM details

- 20 kHz carrier (inaudible, flicker-free), 8-bit resolution (0–255 per channel)
- Timer ISR walks all 12 channels each tick (`pwmCounters < pwmValues → pin HIGH`)
- The timer runs **always** — modes only change the target `pwmValues`
- **Red limit:** when enabled in settings, `setLEDColor()` clamps the R channel to `red_limit_value` (0–255). *SOLID mode is intentionally exempt* (user's exact corner colors win).

---

## 🛡️ Short-circuit protection

| Item | Value |
|---|---|
| **Sense pin** | **GPIO 40**, `INPUT_PULLUP`, active **LOW** |
| **Startup grace** | first **2000 ms** ignored (power-on transients) |
| **Debounce** | short must stay LOW for **100 ms** to count |
| **Reaction** | force mode OFF → LEDs off immediately |
| **Alert** | red blinks: `short_blink_count` × on/off at `short_blink_speed` ms, brightness `short_alert_brightness` |
| **After alert** | previous mode is restored |
| **Logging** | each event appended to `/data.json` on LittleFS: `{unix, time, date, duration(s), timezone}` → shown on the Web UI history page |

During the alert blink the animation loop is paused (`isBlinkingActive()` gates mode updates).

> Wiring assumption: GPIO40 sits on the far side of the LED supply path so a short pulls it LOW (typical resistor-divider/crowbar sense). See `FLASHING.md` if you re-flash: **NVS settings and history survive a normal flash**; only a full `erase_flash` wipes them.

---

## 🔌 Power

- USB 5 V through the CH343 board, or external 5 V supply (3 A+ recommended at full white)
- Each RGB channel is a plain LED load through (assumed) current-limiting resistors — the **red limit** setting exists to cap total draw/heat
- Default fallback AP (Wi-Fi mode): SSID `LEDBOX` @ `http://10.10.10.10` *(rebuild renames to Aurora)*

---

## 🧪 Startup self-test

On boot the firmware sweeps **R → G → B** around the corners (TL→TR→BR→BL), then flashes all-red → all-green → all-blue → all-white, then off. If your box does this on power-up, the PWM wiring is alive.
