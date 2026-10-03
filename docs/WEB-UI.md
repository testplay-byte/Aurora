# 🌐 Web UI — Aurora

The embedded web interface lives in `firmware/aurora/data/` and is served by the ESP32 from **LittleFS** whenever the firmware is built with Wi-Fi enabled (default).

---

## ✅ Current status (rebuild)

- `AURORA_ENABLE_WIFI` is a real `Config.h` switch (**default `1`**): when set, `setup()` runs `webUiBegin()` → STA connect (10 s timeout) → AP fallback → web server + mDNS
- CI compiles **both** variants (Wi-Fi default · BLE-only via `-D AURORA_ENABLE_WIFI=0`) so neither can silently rot
- **100 % offline-safe:** zero CDN requests — native `<input type="color">`, inline SVG icons, system font stack
- All pages and scripts pass `scripts/check-webui.js` (syntax + no-CDN lint) in CI

---

## 📄 Page inventory & navigation

| Page | Purpose |
|---|---|
| `index.html` | Dashboard: big **STOP** + 6 mode buttons (SOLID/BLINK/WAVE/GLOW/TRAIL/SPIRAL), `.active` highlight, toast notifications, 5 s mode poll |
| `mode.html` | Launcher grid → the 6 per-mode pages |
| `solid.html` | 4-corner RGB editor with native color inputs; live preview via `/api/solid/led` (100 ms debounce); **activates SOLID mode on load** so what you see is what the box shows |
| `blink.html` / `wave.html` / `glow.html` / `trail.html` / `spiral.html` | Per-mode parameter editors (raw-ms sliders, shared palette editor, **RUN** applies immediately · **SAVE** persists) |
| `general.html` | Red limit · auto on/off · short-detect tuning · Wi-Fi management (scan + dual STA + AP fallback). **SAVE persists; RESET restores non-network defaults only** — Wi-Fi credentials are never wiped |
| `alert.html` | Live short-circuit feed — **polls `/api/data` every 5 s** (v1 waited on a WebSocket the firmware never served) |
| `history.html` | Logged short events with **real 6 h / 12 h / 24 h range filtering** |
| `secret.html` | Hidden hub linking ALERTS / GENERAL / MODE — back button goes to `/` (works in a fresh tab) |
| `app.css` / `app.js` | Shared dark-glass theme + `App` helpers (`toast`, `get/post`, `App.palette()` shared color editor, `App.secretGate()`) |

### 🔐 The "secret" entry

There are **no ordinary links** out of the dashboard. Clicking **STOP 10 times within 2-second windows** opens `secret.html`. It is a navigation gate only — **no password**. Treat it as an easter egg, not security.

### Mode ↔ page mapping

Index buttons carry `data-mode`; the name table `["OFF","SOLID","BLINK","WAVE","GLOW","TRAIL","SPIRAL"]` matches `settings["MODE_n"]` and firmware `modeSet()`. Only the **dashboard** switches modes (`POST /api/mode`); the mode pages edit their `MODE_n` block (GET → patch → **merge-POST**).

---

## 🔌 HTTP API (firmware ↔ pages)

| Endpoint | Method | Body / Reply |
|---|---|---|
| `/` | GET | `index.html` (explicit route — `serveStatic` alone doesn't serve `/`) |
| `/api/mode` | GET | `{"mode":0-6,"name":"..."}` |
| `/api/mode` | POST | form `mode=3` **or** JSON `{"mode":3}` → `{"ok":true,"mode":3}` |
| `/api/settings` | GET | full settings object (`GENERAL` + `MODE_0..6` + network block) |
| `/api/settings` | POST (JSON) | **deep merge** into current settings → saved to NVS (open tabs can't clobber each other) |
| `/api/data` | GET | `[{unix,time,date,duration,timezone}, ...]` (short history) |
| `/api/wifi/scan` | GET | `[{ssid,rssi,secure}, ...]` |
| `/api/solid/led` | POST | `{"corner":0-3,"r","g","b"}` — set one corner live |
| `/api/solid/save` | POST | persist corner colors into `MODE_1` |
| static files | GET | `serveStatic` last-resort handler → `/index.html`, `*.css/js`, `data.json` |

All routes send `Access-Control-Allow-Origin: *`; `OPTIONS` preflights answer `204`.

**Removed vs v1:** `/api/solid/enter`/`exit`/`colors`/`redlimit` (v1 pages needed enter/exit dances; v2 solid page just activates mode 1 and edits through the merge API), `/settings.html` & `/settings.js` (dead routes), direct `settings.json` download (settings live in NVS only).

---

## 🐛 v1 defects fixed in this rebuild

1. **CDN dependencies break offline** (iro.js, Font Awesome, Google Fonts) → native color input, inline SVG, system fonts — **zero network requests beyond the device itself**
2. **`alert.html` dead WebSocket** (`ws://host:81/`) → polls `/api/data`
3. **5× duplicated mode pages** (~250–300 lines each) → shared `App.palette()` + `app.js` helpers
4. **Slider unit bugs** (raw ms into `min=1 max=100`) → correct raw-ms ranges both directions
5. **RESET wiped Wi-Fi credentials** → RESET patches only `NON_NETWORK_DEFAULTS`
6. **History range buttons only restyled rows** → real filtering
7. **`secret.html` dead back button** → `location.href="/"`
8. **Read-modify-write races** between tabs → server-side **deep merge** on POST `/api/settings`
9. **Dead routes** (`/settings.html`) → removed; `serveStatic` + explicit `/`
10. **Orphaned Wi-Fi flag** (setup never started Wi-Fi) → `webUiBegin()` is real; CI builds both variants

---

## 📁 `data.json` & `settings.json`

| File | Role |
|---|---|
| `data.json` | **Runtime** short-circuit history — written by firmware (`logShortEvent`), read via `/api/data`. Starts as `[]`. Served read-only by `serveStatic`. |
| `settings.json` | ⚠️ **Excluded from the rebuild** — v1's file was stale, publicly downloadable, and contained real home Wi-Fi credentials. Defaults live in code (`settingsCreateDefaults()`), storage in NVS. |

---

## 📶 Network behavior

- **STA:** tries stored credentials with a 10 s timeout; on failure → **AP fallback**
- **AP fallback:** SSID `Aurora-LED` (open) → `http://10.10.10.10` (DHCP enabled for clients)
- **mDNS:** `http://aurora.local` (follows the STA link address, re-binds on reconnect)
- **Schedules** use NTP (`pool.ntp.org`, UTC+5) — no Wi-Fi ⇒ no time ⇒ schedules stay idle until first sync

> **v1 difference:** primary STA used a static `192.168.10.33` — v2 uses **DHCP everywhere** (static IPs stay a per-network choice in the router, not the device).
