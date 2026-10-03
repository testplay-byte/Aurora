# 📱 Android app — Aurora controller

Kotlin + Jetpack Compose app that scans, connects to, and controls the LED box over BLE.

> **📌 Status:** the reference app lives in `old/ledbox-android/` and is **currently untouched** — the rebuild of the app is deliberately deferred (user decision, Oct 2026). This folder (`android/`) will receive the rebuilt app later, with explicit approval. Everything below documents the reference app as-is plus the planned changes.

---

## ✅ What works today

| Feature | Notes |
|---|---|
| **Scan & connect** | 10 s scan, name filter (contains `NTLWC`), auto-connect to last MAC, 30 s timeout, auto-reconnect after 2 s |
| **Control tab** | STOP + 6 mode cards, active mode highlighted from firmware status |
| **Modes tab** | 6 config cards → per-mode editor |
| **Mode editors** | BLINK/WAVE/GLOW: speed + toggles + color palette (max 8); TRAIL: speed/length/brightness/direction; SPIRAL: speed/brightness/direction/pause + 4 colors |
| **SOLID editor** | 4-corner pager + RGB sliders + **WebView live preview**, streams `LED:` commands per change |
| **Logging** | in-app event log (200 entries) — *screen currently unreachable, see below* |
| **Save** | `SAVE` command → firmware persists to NVS |

---

## 🏗️ Architecture (reference app)

```
com.example.ledbox
├── MainActivity.kt            # tabs (Control/Modes/Settings) + mode-edit overlay, permission launcher
├── bluetooth/BleManager.kt    # scan/connect/GATT, command queue, response parsing (531 lines)
├── viewmodel/BleViewModel.kt  # exposes BleManager StateFlows 1:1
├── util/AppLog.kt             # singleton logger → StateFlow (cap 200)
└── ui/screens · components · theme   # dark amber theme, M3
```

- **State:** `BleManager` owns the flows → ViewModel re-exposes → composables `collectAsState()`. Screen-local `remember` for editors.
- **Transport:** colon commands + `\n`, write-no-response, queue with ack/2 s timeout, MTU 517 → see [BLE-PROTOCOL.md](BLE-PROTOCOL.md).
- **Mode edit flow:** enter editor → `GET:MODESETTINGS:n` → JSON parsed into editor state → every change immediately pushes `SET:MODESETTINGS:n:{json}` → **Save** sends `SAVE`.

---

## 🔧 Planned changes for the rebuild

| Area | Change |
|---|---|
| **Package** | `com.example.ledbox` → **`com.aurora.app`** (placeholder name must go before any store/public release) |
| **Device filter** | name contains `NTLWC` → contains **`Aurora`** |
| **Settings screen** | currently ~80% **fake** (Wi-Fi/schedule/brightness cards are local-only, never sent) → wire the real `GENERAL` settings via `GETSETTINGS`/`SETSETTINGS`, drop the Wi-Fi credential cards (Web-UI concern) |
| **Log screen** | built but **unreachable** (`onOpenLogs = {}`) → wire it up |
| **Errors** | `ERR:` responses are parsed but never displayed → show them |
| **Permissions** | add `android:usesPermissionFlags="neverForLocation"` to `BLUETOOTH_SCAN` (drops the pointless location prompt on Android 12+) |
| **Secrets** | hardcoded mock Wi-Fi credentials in `AppSettingsScreen` → removed with the fake cards (values scrubbed from this doc too) |
| **Dead code** | unused `WrapRow`, most of `Components.kt`, unused nav dependency, unused flows → pruned |
| **Tests** | template-only today → unit tests for command encoding + response parsing |

---

## 🛠️ Building (reference app)

```bash
cd ledbox-android        # → future: android/
./gradlew assembleDebug  # APK at app/build/outputs/apk/debug/
```

| Item | Value |
|---|---|
| Gradle wrapper | 9.3.1, JVM toolchain 21 |
| AGP / Kotlin | 9.1.0 / 2.2.10 |
| min/target SDK | 31 / 36 |
| Key deps | Compose BOM, Material3, `material-icons-extended` |
| `local.properties` | machine-specific (`sdk.dir`) — **gitignored, never committed** |

> ⚠️ **No heavy local builds** — CI (`.github/workflows/`) builds the APK on GitHub Actions per the project rule.

---

## 🔐 Permissions

| Permission | Why |
|---|---|
| `BLUETOOTH_SCAN` | find the LED box (add `neverForLocation` in rebuild) |
| `BLUETOOTH_CONNECT` | connect/pair |
| `ACCESS_FINE/COARSE_LOCATION` | legacy path pre-Android 12 — effectively unused at minSdk 31 (rebuild: drop fine-location from the runtime request) |
