# 📱 Aurora — Android controller

Kotlin + Jetpack Compose app that scans, connects to, and controls the Aurora LED box over **Bluetooth LE**.

> **Dark-first UI** (Material3, always-dark Aurora theme — teal `#00C896` on near-black `#0B0F14`), zero network permissions, fully offline.

---

## ✨ Features

| Area | What's inside |
|---|---|
| **Scan & connect** | 10 s scan filtered to devices named `Aurora`, RSSI-sorted, auto-reconnect (2 s, ×5), last-device quick reconnect |
| **Control tab** | live connection chip (RSSI + PING latency), big **STOP**, 6 mode cards with active highlight, one-tap mode switch |
| **Modes tab** | editors for SOLID · BLINK · WAVE · GLOW · TRAIL · SPIRAL |
| **SOLID editor** | 4-corner preview + RGB sliders, live `LED:` streaming, "all corners" apply |
| **Animation editors** | raw-ms sliders, toggles, shared palette editor (add/remove/RGB, max 8; SPIRAL fixed at exactly 4) |
| **Settings tab** | red LED limit, auto on/off schedules (HH:MM validated), short-circuit alert tuning → deep-merge `SETSETTINGS` |
| **Log tab** | live event log (sent `→` / received `←` / system `·`), 200 entries, clearable — **reachable** |
| **Feedback** | every `ERR:` from the firmware surfaces as a snackbar; commands time out loudly |

## 📡 Protocol

Speaks exactly [docs/BLE-PROTOCOL.md](../docs/BLE-PROTOCOL.md): custom UUIDs `00004faf/beb5/beb6`, 12 colon-ASCII newline-terminated commands, write-no-response, MTU 517, FIFO request/response matching.

```
ble/BleProtocol.kt   pure command builders + response parser (unit-tested)
ble/SettingsJson.kt  GENERAL + MODE_n schemas ⇄ typed editor state (unit-tested)
ble/BleManager.kt    scan/connect/GATT/serialized write pump/line reassembly
```

## 🏗️ Structure

```
app/src/main/java/com/aurora/app/
├── MainActivity.kt        # permissions, routes, toasts, bottom nav
├── ble/                   # protocol + transport (above)
├── model/Mode.kt          # 7-mode table (icon + id ↔ firmware names)
├── viewmodel/             # AuroraViewModel: routes, mode cache, editors
├── ui/theme/              # always-dark Material3 scheme
├── ui/components/         # SectionCard, sliders, palette editor, mode cards
└── ui/screens/            # Scan · Control · Modes · Editor · Settings · Log
```

## 🛠️ Build

**CI builds it** (user rule: no heavy local builds) — `.github/workflows/android.yml`:

```bash
cd android
./gradlew test assembleDebug      # unit tests + app-debug.apk
```

Toolchain (pinned): AGP **9.1.0** · Kotlin **2.2.10** · Gradle **9.3.1** · Compose BOM **2024.09.00** · minSdk 31 / target 36 · JDK 21 (CI).

## ✅ Tests

`app/src/test/` — all 12 command strings, every response parser branch, line-buffer splitting, GENERAL round-trips + time validation, all 6 mode schemas (doc examples + clamping + round-trip). Run with `./gradlew test`.
