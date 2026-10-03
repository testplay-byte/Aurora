// ============================================================================
// Aurora — ShortDetect.cpp
//
// GPIO40 (INPUT_PULLUP, active LOW) is pulled low by a shorted output stage.
// Sequence: 2 s boot grace → 100 ms debounce → force OFF → red alert blinks
// (count/speed/brightness from GENERAL settings) → log to /data.json →
// restore the mode that was running.
// ============================================================================
#include "ShortDetect.h"
#include "Config.h"
#include "Settings.h"
#include "SoftPwm.h"
#include "Modes.h"
#include <LittleFS.h>
#include <time.h>

static bool active = false;          // short currently present
static bool pending = false;         // debounce in progress
static uint32_t pendingSince = 0;
static uint32_t shortStart = 0;
static bool logged = false;

static bool alertRunning = false;
static int blinksDone = 0;
static bool blinkPhase = false;      // true = LEDs on
static uint32_t lastBlink = 0;
static int previousMode = 0;

static uint32_t bootedAt = 0;

static void logShortEvent(uint32_t durationMs);

void shortDetectBegin() {
  pinMode(AURORA_SHORT_PIN, INPUT_PULLUP);
  bootedAt = millis();
  pending = false;
  Serial.printf("[short] sense pin %d ready (%lu ms grace, %lu ms debounce)\n",
                AURORA_SHORT_PIN, (unsigned long)AURORA_SHORT_GRACE_MS,
                (unsigned long)AURORA_SHORT_DEBOUNCE_MS);
}

static void startAlert() {
  previousMode = modeActive();
  softPwmOff();
  modeSet(0);
  alertRunning = true;
  blinksDone = 0;
  blinkPhase = false;
  lastBlink = 0;
  Serial.printf("[short] SHORT CIRCUIT detected (mode %d suspended)\n", previousMode);
}

static void runAlert() {
  JsonObject g = settingsGeneral();
  int count = g["short_blink_count"] | 3;
  int speed = g["short_blink_speed"] | 200;
  int level = g["short_alert_brightness"] | 128;

  uint32_t now = millis();
  if (lastBlink != 0 && now - lastBlink < (uint32_t)speed) return;
  lastBlink = now;

  if (blinksDone < count * 2) {          // count on/off transitions
    blinkPhase = !blinkPhase;
    if (blinkPhase) softPwmSetAll(level, 0, 0, 255);
    else            softPwmOff();
    blinksDone++;
    return;
  }

  // done — restore the interrupted mode
  softPwmOff();
  alertRunning = false;
  if (previousMode > 0 && previousMode != modeActive()) {
    modeSet(previousMode);
    Serial.printf("[short] alert finished — mode %d restored\n", previousMode);
  } else {
    Serial.println("[short] alert finished — staying OFF");
  }
}

void shortDetectUpdate() {
  uint32_t now = millis();

  // Boot grace: ignore the pin, but let a pre-existing alert finish.
  if (now - bootedAt < AURORA_SHORT_GRACE_MS) {
    if (alertRunning) runAlert();
    return;
  }

  bool low = digitalRead(AURORA_SHORT_PIN) == LOW;

  if (low && !active) {
    if (!pending) {
      pending = true;
      pendingSince = now;
    } else if (now - pendingSince >= AURORA_SHORT_DEBOUNCE_MS) {
      pending = false;
      active = true;
      logged = false;
      shortStart = now;
      startAlert();
    }
  } else if (!low) {
    pending = false;
    if (active) {
      uint32_t duration = now - shortStart;
      active = false;
      Serial.printf("[short] cleared after %lu ms\n", (unsigned long)duration);
      if (duration > 1 && !logged) {
        logShortEvent(duration);
        logged = true;
      }
    }
  }

  if (alertRunning) runAlert();
}

bool shortAlertActive() {
  return alertRunning;
}

bool shortDetected() {
  return active;
}

// ---------------------------------------------------------------------------
// History: append {unix,time,date,duration,timezone} to /data.json
// (served to the Web UI via GET /api/data). Best-effort: never blocks the
// alert logic for long, never crashes on a corrupt file.
// ---------------------------------------------------------------------------
static String fmtTime(time_t t) {
  char buf[6];
  struct tm* ti = localtime(&t);
  if (!ti) return "00:00";
  strftime(buf, sizeof(buf), "%H:%M", ti);
  return String(buf);
}

static String fmtDate(time_t t) {
  char buf[10];
  struct tm* ti = localtime(&t);
  if (!ti) return "Jan 01";
  strftime(buf, sizeof(buf), "%b %d", ti);
  return String(buf);
}

static void logShortEvent(uint32_t durationMs) {
  time_t now = time(nullptr);
  if (now < 1000000000) {
    // No NTP yet — fall back to a monotonic approximation
    now = millis() / 1000 + 1640995200;   // ≈ 2022-01-01
  }

  JsonDocument doc;
  File file = LittleFS.open("/data.json", "r");
  if (file) {
    DeserializationError err = deserializeJson(doc, file);
    file.close();
    if (err) doc.to<JsonArray>();
  } else {
    doc.to<JsonArray>();
  }

  JsonObject e = doc.add<JsonObject>();
  e["unix"] = (int32_t)now;
  e["time"] = fmtTime(now);
  e["date"] = fmtDate(now);
  e["duration"] = durationMs / 1000.0;
  e["timezone"] = "UTC+5";

  file = LittleFS.open("/data.json", "w");
  if (file) {
    serializeJsonPretty(doc, file);
    file.close();
    Serial.printf("[short] logged %.2fs to /data.json\n", durationMs / 1000.0);
  }
}
