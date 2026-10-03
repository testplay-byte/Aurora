# 🌌 Aurora — Project overview

The story, architecture, and decisions behind Aurora — the ESP32-S3 LED box. **This document replaces the old `PROJECTS_OVERVIEW.md`**, which pointed at a dissolved folder and described things that no longer exist.

---

## 📖 What this project is

A **BLE-controlled 4-corner RGB LED box** built on an ESP32-S3, with:

1. **`firmware/`** — Arduino C++ firmware: 7 animation modes, BLE command service, short-circuit protection, NVS settings, optional Wi-Fi Web UI served from LittleFS
2. **`android/`** — Kotlin/Jetpack Compose controller app *(reference version analyzed; rebuild deferred by decision)*
3. **`docs/`** — this documentation set

---

## 🏛️ Architecture at a glance

```
 ┌────────────────────────── ESP32-S3 (Aurora box) ──────────────────────────┐
 │  BLE GATT service ──── 12 text commands ────► mode engine (MODE_0..6)     │
 │  (primary, always on)                          │                         │
 │                                                ▼                         │
 │  HTTP /api/* (AURORA_ENABLE_WIFI, default 1) 20 kHz soft-PWM ─► 4 RGB LEDs │
 │  └─ Web UI from LittleFS                     short-detect GPIO40 → alert  │
 │                                               settings → NVS (survives)   │
 └───────────────────────────────────────────────────────────────────────────┘
          ▲                                     ▲
          │ BLE (custom UUIDs, colon+NL)        │ HTTP (Wi-Fi / AP mode)
 ┌────────┴─────────┐                  ┌────────┴─────────┐
 │ Android app      │                  │ Browser          │
 │ (main UI today)  │                  │ (optional)       │
 └──────────────────┘                  └──────────────────┘
```

**Two protocols, one state machine.** Both end in the same `settings` JSON + `modeSet()` — BLE is primary; the HTTP path is fully wired in the rebuild (`webUiBegin()` runs in `setup()` whenever the Wi-Fi flag is on).

---

## 🎨 The 7 modes

| # | Name | Behavior |
|---|---|---|
| 0 | **OFF** | all LEDs off |
| 1 | **SOLID** | 4 independent corner colors (TL/TR/BL/BR) |
| 2 | **BLINK** | on/off or no-off color cycling at `interval` |
| 3 | **WAVE** | two diagonal corner groups sine-fade against each other, random palette swaps at zero-cross |
| 4 | **GLOW** | breathing brightness + optional smooth color transitions |
| 5 | **TRAIL** | comet trail with 0.6ⁿ decay, sub-pixel blending across corners |
| 6 | **SPIRAL** | 4-frame rotating color pattern |

Details + JSON schemas: [BLE-PROTOCOL.md](BLE-PROTOCOL.md). Pin/corner mapping: [HARDWARE.md](HARDWARE.md).

---

## 📜 Naming history (why "Aurora")

| When | Name | Where it survived |
|---|---|---|
| early | `ESP32_PROJECT` → **ESP32 LED BOX** | root folder (original, untouched) |
| 2025–26 | **NTLWC** ("Next-Level LED Wave Controller") | sketch folder, BLE device `NTLWC-LED`, mDNS `ntlwc.local`, web header |
| 2025–26 | **LEDBOX** | Android app label, default AP SSID |
| **2026-10-04** | **Aurora** ✅ | one name for repo, device, host, app, sketch |

The tagline "Next-Level LED Wave Controller" lives on as the subtitle.

---

## ✅ Decisions log

| Date | Decision |
|---|---|
| 2026-10-04 | Renamed to **Aurora** (single unified name) |
| 2026-10-04 | Rebuild from scratch; old layout is reference only — proven logic ported, defects fixed |
| 2026-10-04 | **Android app deferred**: `android/` placeholder only; rebuild later with explicit permission |
| 2026-10-04 | Real home Wi-Fi names/passwords scrubbed from all ported files; other placeholders left alone |
| 2026-10-04 | `settings.json` excluded from the repo (stale + leaked credentials) |
| 2026-10-04 | `ENABLE_WIFI` must genuinely initialize in `setup()`; CI compiles BLE-only **and** Wi-Fi variants |
| 2026-10-04 | Web UI goes 100 % offline-safe (no CDN: native color input, inline SVG, system fonts) |
| 2026-10-04 | Reference firmware flash-tested on real ESP32-S3 (COM6) before rebuilding |
| 2026-10-04 | No heavy local builds: firmware CI via arduino-cli, APK via GitHub Actions |
| 2026-10-04 | **16 MB flash** probed → partition scheme **`default_8MB`** (3.3 MB app + 1.5 MB LittleFS @ `0x670000`); v1's 1.25 MB default app partition can't hold the Wi-Fi build |
| 2026-10-04 | **DHCP everywhere** — dropped v1's static `192.168.10.33` primary STA; AP fallback `Aurora-LED` @ `10.10.10.10` kept |
| 2026-10-04 | `POST /api/settings` **deep-merges** (fixes v1's whole-object clobber races); `SETSETTINGS` BLE command deep-merges too |
| pending | license choice (none yet) |

---

## 🧾 What the rebuild changes (summary)

**Kept (proven logic):** 7 modes · solid corner editor · 20 kHz PWM · short-detect debounce/blink/log · 12-command BLE protocol · NVS per-mode settings · auto on/off · startup animation · Web UI pages & API.

**Fixed:** orphaned `ENABLE_WIFI` · dead WebSocket → polling · CDN-offline breakage · 5× duplicated mode pages · slider unit bugs · RESET wiping Wi-Fi · history filter no-op · dead routes · `com.example` package · fake settings screens · unreachable log screen · hidden `ERR:` messages · missing `neverForLocation`.

**Dropped:** `12 LED SLIDER` experiments (reference only in `old/`) · stale `PROJECTS_OVERVIEW.md` · `local.properties` · secret-bearing `settings.json` · dead code/routes.

---

## 🔐 Security notes

- **BLE has no auth** — anyone nearby can control a paired-connection-less device. Acceptable for a bedroom light; noted for the future.
- **HTTP has no auth** and (when enabled) exposes settings — another reason Wi-Fi stays behind a flag.
- The Web UI "secret" menu is a 10-click gate, **not** security.
- Repo hygiene: credentials live only in `GITHUB-DETAILS.md` **outside** this repo; leak-scan before first push.

---

## 🗺️ Roadmap

1. ✅ Analyze + document + flash-test reference firmware
2. ✅ Rebuild `firmware/` (clean modules, real `AURORA_ENABLE_WIFI`, offline web UI) — both variants compile
3. ✅ CI: web-UI lint + firmware matrix (BLE-only + Wi-Fi) *(APK build added with the app)*
4. 🔄 Push to `github.com/testplay-byte/Aurora` → CI green → flash + live-verify the box
5. ⬜ Rebuild Android app (`com.aurora.app`) — **on approval**
6. ⬜ Optional: license, OTA updates, device rename rollout
