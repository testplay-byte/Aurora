// ============================================================================
// Aurora — Schedule.cpp
//
// Turns the box ON/OFF at configured HH:MM. Needs valid time from NTP —
// without a network, time() never advances past the epoch check and the
// scheduler idles (documented behavior).
//
// A trigger fires when the current minute is within ±1 minute of the target
// and only once per crossing (latched until we leave the window).
// ============================================================================
#include "Schedule.h"
#include "Config.h"
#include "Settings.h"
#include "Modes.h"
#include <time.h>

static bool onLatched = false;
static bool offLatched = false;

static int parseMinutes(const String& hhmm) {
  int colon = hhmm.indexOf(':');
  if (colon <= 0) return -1;
  int h = hhmm.substring(0, colon).toInt();
  int m = hhmm.substring(colon + 1).toInt();
  return h * 60 + m;
}

void scheduleSyncTime() {
  configTime(AURORA_GMT_OFFSET_SEC, AURORA_DST_OFFSET_SEC, AURORA_NTP_SERVER);
  Serial.println("[time] NTP sync requested (" AURORA_NTP_SERVER ")");
}

void scheduleCheck() {
  time_t now = time(nullptr);
  struct tm* ti = localtime(&now);
  if (!ti || ti->tm_year < 100) return;   // time not valid yet

  int nowMin = ti->tm_hour * 60 + ti->tm_min;
  JsonObject g = settingsGeneral();

  // --- auto ON ---
  if (g["auto_turn_on_enabled"].as<bool>()) {
    int target = parseMinutes(g["auto_turn_on_time"] | "18:00");
    if (target >= 0) {
      bool inWindow = abs(nowMin - target) <= 1;
      if (inWindow && !onLatched) {
        if (modeActive() == 0) {
          int restore = settingsLastActiveMode();
          modeSet(restore);
          Serial.printf("[time] auto-ON -> mode %d\n", restore);
        }
        onLatched = true;
      } else if (!inWindow) {
        onLatched = false;
      }
    }
  }

  // --- auto OFF ---
  if (g["auto_turn_off_enabled"].as<bool>()) {
    int target = parseMinutes(g["auto_turn_off_time"] | "23:00");
    if (target >= 0) {
      bool inWindow = abs(nowMin - target) <= 1;
      if (inWindow && !offLatched) {
        if (modeActive() != 0) {
          settingsSetLastActiveMode(modeActive());
          modeSet(0);
          Serial.println("[time] auto-OFF");
        }
        offLatched = true;
      } else if (!inWindow) {
        offLatched = false;
      }
    }
  }
}
