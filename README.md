# 🌌 Aurora

**Next-Level LED Wave Controller** — an ESP32-S3 powered LED box with **7 animation modes**, controlled over **Bluetooth LE** from the Android app, with an optional **Wi-Fi Web UI** and built-in **short-circuit protection**.

> Formerly known as *ESP32 LED BOX* / *NTLWC* / *LEDBOX* — unified under the single name **Aurora** in 2026.

---

## ✨ Features

| Feature | Description |
|---|---|
| 🎨 **7 animation modes** | OFF · SOLID · BLINK · WAVE · GLOW · TRAIL · SPIRAL |
| 📱 **BLE control** | Custom GATT service, 12 text commands, device name **`Aurora`** |
| 🌐 **Web UI** | Served from LittleFS — control pages per mode, history, settings (Wi-Fi **on by default**, `AURORA_ENABLE_WIFI=0` to disable) · 100 % offline-safe (no CDNs) |
| 🛡️ **Short-circuit protection** | Data-line sense pin → debounced trigger → red alert blinks → event logged → mode restored |
| 💾 **Persistent settings** | Every mode's settings + schedules stored in ESP32 NVS — survive power loss |
| ⏰ **Auto on/off** | Scheduled turn-on/off (needs Wi-Fi time sync) |
| 🔴 **Red LED limit** | Caps the red channel globally (heat/power protection) |
| 🚀 **Startup animation** | Color sweep self-test on boot |

---

## 🏗️ Repository structure

```
Aurora/
├── README.md            # you are here
├── firmware/aurora/     # ESP32-S3 Arduino firmware (modular: Config/Settings/SoftPwm/
│                        #   Modes/Ble/WebUi/ShortDetect/Schedule) + data/ web UI
├── scripts/             # web-UI lint (runs in CI), serial boot-check helper
├── android/             # Aurora Android controller app — placeholder, rebuild deferred
├── docs/                # complete documentation set (start with docs/OVERVIEW.md)
└── .github/workflows/   # CI: web lint + firmware matrix (Wi-Fi & BLE-only)
```

---

## 🚀 Quick start

1. **Flash the firmware** → [docs/FLASHING.md](docs/FLASHING.md)
2. **Pair the app** → [docs/ANDROID.md](docs/ANDROID.md)
3. **(Optional) Web UI** → [docs/WEB-UI.md](docs/WEB-UI.md)
4. **Understand the hardware** → [docs/HARDWARE.md](docs/HARDWARE.md)

New here? Read **[docs/OVERVIEW.md](docs/OVERVIEW.md)** first — architecture, naming history, and how the three parts talk to each other.

---

## 📚 Documentation

| Doc | What's inside |
|---|---|
| [OVERVIEW](docs/OVERVIEW.md) | Project story, architecture, dual-protocol map, decisions log |
| [HARDWARE](docs/HARDWARE.md) | Board, pin maps, LED wiring, power, short-detect circuit |
| [FLASHING](docs/FLASHING.md) | Compile + upload (CLI & IDE), LittleFS data upload, serial monitor |
| [BLE-PROTOCOL](docs/BLE-PROTOCOL.md) | UUIDs, all 12 commands, JSON schemas, error codes |
| [WEB-UI](docs/WEB-UI.md) | Pages, HTTP API, navigation secrets, offline notes |
| [ANDROID](docs/ANDROID.md) | App setup, permissions, screens, build |

---

## 🧪 Status

- ✅ Reference firmware analyzed, documented, and flash-tested on real hardware (ESP32-S3, Oct 2026)
- ✅ **Rebuild complete:** modular firmware (Wi-Fi on by default), offline-safe 12-page Web UI, NVS migration `ntlwc` → `aurora`, CI matrix (Wi-Fi + BLE-only)
- ✅ Both variants compile locally: **Wi-Fi 1,321,369 B (39 %)** · **BLE-only 829,449 B (24 %)** of the 3.3 MB `default_8MB` app partition (16 MB flash)
- ⬜ Android app rebuild — **deferred until explicit approval**

## 📄 License

Not chosen yet — all rights reserved until a license is added.
