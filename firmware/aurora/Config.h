// ============================================================================
// Aurora — Config.h
// Single source of truth for hardware pins, naming, IDs and feature flags.
// ============================================================================
#ifndef AURORA_CONFIG_H
#define AURORA_CONFIG_H

#include <Arduino.h>

// ---------------------------------------------------------------------------
// Identity — one name everywhere (repo, BLE, mDNS, sketch)
// ---------------------------------------------------------------------------
#define AURORA_DEVICE_NAME   "Aurora"        // BLE advertised name
#define AURORA_HOSTNAME      "aurora"        // mDNS: http://aurora.local
#define AURORA_NVS_NAMESPACE "aurora"        // Preferences namespace (settings)
#define AURORA_LEGACY_NAMESPACE "ntlwc"      // old firmware namespace (migrated once)
#define AURORA_VERSION       "2.0.0"

// ---------------------------------------------------------------------------
// Feature flags
// ---------------------------------------------------------------------------
// Wi-Fi + embedded Web UI. CI compiles BOTH variants so neither can rot:
//   AURORA_ENABLE_WIFI=1  → Wi-Fi connect + HTTP server + LittleFS web UI
//   AURORA_ENABLE_WIFI=0  → BLE-only build (smaller, no network stack used)
#ifndef AURORA_ENABLE_WIFI
#define AURORA_ENABLE_WIFI 1
#endif

// ---------------------------------------------------------------------------
// LEDs — 4 discrete RGB corners driven by 20 kHz software PWM
// (NOT an addressable strip: plain PWM dimming on 12 GPIOs)
// ---------------------------------------------------------------------------
#define AURORA_LED_COUNT      4
#define AURORA_CHANNELS       3   // R, G, B per LED

// Physical pin map per LED:  {R, G, B}
//   LED0 = Top-Right   LED1 = Top-Left   LED2 = Bottom-Left   LED3 = Bottom-Right
#define AURORA_LED_PINS { {11, 13, 14}, {4, 5, 6}, {7, 15, 16}, {8, 9, 10} }

// Corner indices used by the app / web UI (TL, TR, BL, BR) → physical LED.
// The ONLY place this mapping is defined — every module includes this table.
#define AURORA_CORNER_TO_LED { 1, 0, 2, 3 }   // TL→LED1, TR→LED0, BL→LED2, BR→LED3

#define AURORA_PWM_FREQUENCY_HZ 20000         // flicker-free, inaudible

// ---------------------------------------------------------------------------
// Short-circuit protection (sense line, active LOW)
// ---------------------------------------------------------------------------
#define AURORA_SHORT_PIN            40
#define AURORA_SHORT_GRACE_MS       2000      // ignore boot transients
#define AURORA_SHORT_DEBOUNCE_MS    100       // LOW must persist to count

// ---------------------------------------------------------------------------
// Time / schedules (NTP)
// ---------------------------------------------------------------------------
#define AURORA_NTP_SERVER     "pool.ntp.org"
#define AURORA_GMT_OFFSET_SEC  (5 * 3600)     // UTC+5 — adjust for your zone
#define AURORA_DST_OFFSET_SEC  0

// ---------------------------------------------------------------------------
// Wi-Fi defaults (overridable at runtime via the Web UI's general page)
// ---------------------------------------------------------------------------
#define AURORA_AP_SSID        "Aurora-LED"    // fallback access point
#define AURORA_AP_PASSWORD     "auroraled"    // ≥8 chars, or "" for open AP
#define AURORA_STA1_TIMEOUT_S  10
#define AURORA_STA2_TIMEOUT_S  15

// ---------------------------------------------------------------------------
// BLE service — custom UUIDs (kept from v1 so tooling stays compatible)
// ---------------------------------------------------------------------------
#define AURORA_BLE_SERVICE_UUID     "00004faf-0000-1000-8000-00805f9b34fb"
#define AURORA_BLE_COMMAND_CHAR_UUID "0000beb5-0000-1000-8000-00805f9b34fb"
#define AURORA_BLE_RESPONSE_CHAR_UUID "0000beb6-0000-1000-8000-00805f9b34fb"

#define AURORA_BLE_CMD_BUFFER_MAX   2048      // drop pathological input

// ---------------------------------------------------------------------------
// Serial
// ---------------------------------------------------------------------------
#define AURORA_SERIAL_BAUD   115200

#endif  // AURORA_CONFIG_H
