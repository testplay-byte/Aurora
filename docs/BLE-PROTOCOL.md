# 📡 BLE Protocol — Aurora

The **primary control channel**: a BLE GATT service with one write characteristic (commands in) and one notify characteristic (responses out). The Android app speaks exactly this protocol.

> **Naming:** v2 firmware advertises as **`Aurora`** (v1 used `NTLWC-LED` — the Android app's name filter is updated together with the rename).

---

## 🔑 UUIDs

| Item | UUID |
|---|---|
| **Service** | `00004faf-0000-1000-8000-00805f9b34fb` |
| **Command characteristic** (Phone → ESP32, WRITE) | `0000beb5-0000-1000-8000-00805f9b34fb` |
| **Response characteristic** (ESP32 → Phone, READ + NOTIFY) | `0000beb6-0000-1000-8000-00805f9b34fb` |
| **CCCD** (notify enable) | `00002902-0000-1000-8000-00805f9b34fb` |

⚠️ These are **custom UUIDs**, not the classic Nordic UART set (`6E400001…`) — the old overview doc was wrong about this.

---

## 📬 Framing rules

- Commands are **ASCII text**, colon-separated fields, **newline-terminated** (`\n`)
- The firmware buffers incoming writes and only processes complete lines (buffer cap **2048 bytes**, then dropped)
- The app writes with **WRITE_NO_RESPONSE** and requests **MTU 517** → payloads up to ~514 bytes per notification are safe
- Responses arrive as **single notifications** — one response per command; there is **no message reassembly**, so keep payloads under the MTU (mode-settings JSON is kept ≤ 4 colors × 8 entries for this reason)
- The app sends `STATUS` right after service discovery to sync the current mode

---

## 🎛️ Commands (Phone → ESP32)

| # | Command | Meaning | Success response | Errors |
|---|---|---|---|---|
| 1 | `GET:MODE` | current mode | `MODE:<0-6>:<NAME>` | — |
| 2 | `SET:MODE:<0-6>` | switch mode | `OK:MODE:<n>:<NAME>` | `ERR:INVALID_MODE` |
| 3 | `OFF` | → mode 0 (all LEDs off) | `OK:OFF` | — |
| 4 | `LED:<corner>:<r>,<g>,<b>` | live-set one corner (0–3) | `OK:LED:<c>:<r>,<g>,<b>` | `ERR:INVALID_PARAMS`, `ERR:INVALID_COLOR` |
| 5 | `ALL:<r>,<g>,<b>` | all LEDs one color | `OK:ALL:<r>,<g>,<b>` | `ERR:INVALID_COLOR` |
| 6 | `SAVE` | persist SOLID colors to NVS | `OK:SAVED` | — |
| 7 | `STATUS` | mode status (sent on connect) | `STATUS:<0-6>:<NAME>` | — |
| 8 | `PING` | connection test | `PONG` | — |
| 9 | `GETSETTINGS` | full settings JSON | `SETTINGS:<json>` | — |
| 10 | `SETSETTINGS:<json>` | **deep-merge** the JSON into settings, save | `OK:SETTINGS_SAVED` | `ERR:INVALID_JSON` |
| 11 | `GET:MODESETTINGS:<0-6>` | one mode's settings | `MODESETTINGS:<n>:<json>` | `ERR:INVALID_MODE` |
| 12 | `SET:MODESETTINGS:<0-6>:<json>` | replace one mode's settings, save to NVS | `OK:MODESETTINGS_SAVED:<n>` | `ERR:INVALID_JSON`, `ERR:INVALID_MODE`, `ERR:INVALID_FORMAT` |

Anything unrecognized → `ERR:UNKNOWN_CMD`.

> `SETSETTINGS` **deep-merges**: nested objects merge key-by-key, scalars/arrays replace. Send `{"GENERAL":{"short_blink_count":5}}` to change one field without touching the rest.

---

## 🧩 Mode settings JSON schemas

Settings live under keys `MODE_0` … `MODE_6` + `GENERAL`. Shapes per mode (as used by app + firmware):

### `MODE_0` — OFF
```json
{ "name": "OFF" }
```

### `MODE_1` — SOLID (4 corner colors: TL, TR, BL, BR)
```json
{ "name": "SOLID", "colors": [[r,g,b],[r,g,b],[r,g,b],[r,g,b]] }
```
Live editing goes through `LED:` commands; `SAVE` persists.

### `MODE_2` — BLINK
```json
{ "name": "BLINK", "interval": 500, "random": true, "no_off": false,
  "colors": [[r,g,b], …max 8] }
```
`interval` in ms. `no_off: true` = cycle colors without an off phase.

### `MODE_3` — WAVE
```json
{ "name": "WAVE", "interval": 50, "pattern": "diagonal",
  "colors": [[r,g,b], …max 8] }
```
Two diagonal corner groups sine against each other; at each zero-cross a group picks a random new palette color (≠ the other group's).

### `MODE_4` — GLOW
```json
{ "name": "GLOW", "interval": 30, "smooth_transitions": false,
  "breathing_effect": true, "colors": [[r,g,b], …max 8] }
```

### `MODE_5` — TRAIL
```json
{ "name": "TRAIL", "speed": 1000, "direction": true,
  "trail_length": 3, "brightness": 100, "colors": [[r,g,b], …] }
```
`speed` ms per lap (0–100 UI), `brightness` 0–100, `trail_length` 1–4 (decay 0.6ⁿ, sub-pixel blending between corners).

### `MODE_6` — SPIRAL
```json
{ "name": "SPIRAL", "speed": 1000, "direction": true,
  "brightness": 100, "paused": false, "colors": [[r,g,b]×4] }
```
Exactly 4 colors, rotated through the corners frame-by-frame.

### `GENERAL`
```json
{ "red_limit_enabled": false, "red_limit_value": 255,
  "auto_turn_on_enabled": false, "auto_turn_on_time": "18:00",
  "auto_turn_off_enabled": false, "auto_turn_off_time": "23:00",
  "short_blink_count": 3, "short_blink_speed": 200,
  "short_alert_brightness": 128 }
```
*(Wi-Fi credential fields exist in the Web-UI variant — see WEB-UI.md; they are not managed over BLE.)*

---

## 🔄 Connection lifecycle (app side)

1. **Scan** (10 s max) → filter: advertised name **contains** `NTLWC` (rebuild: `Aurora`)
2. **Connect** → auto-reconnect to last MAC (`SharedPreferences`), 30 s timeout, 2 s retry
3. **MTU 517** negotiated
4. **Service discovery** → enable CCCD notifications on `…beb6`
5. Send **`STATUS`** → firmware replies with current mode → UI syncs
6. Edits stream as `SET:MODESETTINGS:` per control change; **Save** button sends `SAVE`

## 💾 Persistence

- `SET:MODESETTINGS:` / `SAVE` / `SET:MODE` write to **NVS** (`Preferences`, namespace `ntlwc`) — survive power loss
- `lastMode` restored on boot; auto-on/off needs NTP time (Wi-Fi)
