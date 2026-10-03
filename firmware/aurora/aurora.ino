// ============================================================================
//                              A U R O R A
//              Next-Level LED Wave Controller — ESP32-S3
//
// Thin entry point: initialize each module, restore the boot mode, then
// loop the three services (BLE, protection, active animation) + web UI.
//
// Module map (all in this folder, flat Arduino layout):
//   Config.h      pins, names, UUIDs, feature flags      (single source)
//   Settings.*    NVS settings + v1 migration + deep merge
//   SoftPwm.*     20 kHz PWM for the 12 LED channels
//   Modes.*       mode registry (begin/tick/end) + switching
//   Mode*.cpp     the 7 animations
//   Solid.*       4-corner persistent colors
//   Ble.*         BLE command service (12 commands)
//   ShortDetect.* short-circuit protection + history log
//   Schedule.*    auto on/off + NTP
//   SelfTest.*    boot animation
//   WebUi.*       Wi-Fi + HTTP + LittleFS pages (AURORA_ENABLE_WIFI)
//   data/         web UI source, uploaded to LittleFS (see docs/FLASHING.md)
// ============================================================================
#include "Config.h"
#include "Settings.h"
#include "SoftPwm.h"
#include "Modes.h"
#include "Ble.h"
#include "ShortDetect.h"
#include "Schedule.h"
#include "WebUi.h"
#include <LittleFS.h>

void selfTestRun();   // SelfTest.cpp

static uint32_t lastScheduleCheck = 0;
static uint32_t lastNtpRefresh = 0;

void setup() {
  Serial.begin(AURORA_SERIAL_BAUD);
  delay(300);
  Serial.printf("\n========================================\n"
                "  Aurora v%s — Next-Level LED Wave Controller\n"
                "  Build: %s\n"
                "========================================\n",
                AURORA_VERSION,
#if AURORA_ENABLE_WIFI
                "Wi-Fi + BLE"
#else
                "BLE-only"
#endif
  );

  // 1. Filesystem (settings live in NVS; the FS holds short-history data.json
  //    and — in the Wi-Fi build — the web UI pages)
  Serial.println("[1/7] Mounting LittleFS…");
  if (!LittleFS.begin(true)) Serial.println("      mount failed (continuing)");

  // 2. Settings (with one-time migration from the v1 'ntlwc' namespace)
  Serial.println("[2/7] Loading settings…");
  settingsLoad();

  // 3. LED driver + boot self test
  Serial.println("[3/7] Starting 20 kHz PWM…");
  softPwmBegin();
  Serial.println("      Running boot self-test…");
  selfTestRun();

  // 4. Short-circuit protection (2 s grace, 100 ms debounce)
  Serial.println("[4/7] Short-circuit protection…");
  shortDetectBegin();

  // 5. Animation modes
  Serial.println("[5/7] Loading 7 modes…");
  modesInit();

  // 6. BLE (advertises as AURORA_DEVICE_NAME)
  Serial.println("[6/7] Starting BLE…");
  bleBegin();

  // 7. Network + web UI (no-op in BLE-only builds — see WebUi.cpp)
  Serial.println("[7/7] Web UI…");
  webUiBegin();

  // Restore the last active mode (defaults to OFF)
  int saved = settingsLastMode();
  modeSet(saved);

  Serial.println("========================================\n"
                 "  SETUP COMPLETE\n");
  Serial.printf("  BLE:       %s (advertising)\n", AURORA_DEVICE_NAME);
  Serial.printf("  Mode:      %d %s\n", modeActive(), modeName(modeActive()));
  Serial.printf("  Web UI:    %s\n",
                webUiActive() ? "http://aurora.local (or AP 10.10.10.10)"
                              : "disabled (BLE-only build)");
  Serial.printf("  Free heap: %u bytes\n\n", (unsigned)ESP.getFreeHeap());
}

void loop() {
  bleLoop();                       // re-advertise after disconnects
  shortDetectUpdate();             // always running (safety)

  // Auto on/off — checked once a minute; idles until NTP gives valid time
  if (millis() - lastScheduleCheck > 60000) {
    scheduleCheck();
    lastScheduleCheck = millis();
  }

  // Keep NTP fresh (Wi-Fi build; configTime is a no-op without a link)
  if (millis() - lastNtpRefresh > 3600000) {
    scheduleSyncTime();
    lastNtpRefresh = millis();
  }

  // Animate — paused while a short-circuit alert owns the LEDs
  if (!shortAlertActive()) {
    modeTick(millis());
  }

  webUiLoop();                     // no-op in BLE-only builds
  delay(1);
}
