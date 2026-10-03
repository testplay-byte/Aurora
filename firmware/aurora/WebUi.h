// ============================================================================
// Aurora — WebUi.h
// Wi-Fi + HTTP server + LittleFS web UI. Entirely compiled out when
// AURORA_ENABLE_WIFI=0 (BLE-only build) — the functions still exist as
// no-ops so aurora.ino never needs #ifdefs of its own.
// Reference: docs/WEB-UI.md
// ============================================================================
#ifndef AURORA_WEBUI_H
#define AURORA_WEBUI_H

#include <Arduino.h>

void webUiBegin();     // connect Wi-Fi (STA→STA2→AP), mount routes, start server
void webUiLoop();      // handleClient + mDNS upkeep (cheap when idle)
bool webUiActive();    // true when the HTTP server is running

#endif  // AURORA_WEBUI_H
