// ============================================================================
// Aurora — Solid.cpp
// ============================================================================
#include "Solid.h"
#include "Config.h"
#include "Settings.h"
#include "SoftPwm.h"

static const uint8_t kCornerToLed[AURORA_LED_COUNT] = AURORA_CORNER_TO_LED;

// Corner colors in user space (TL, TR, BL, BR)
static uint8_t cornerColors[AURORA_LED_COUNT][3] = {
  {255, 0, 0},        // TL
  {255, 255, 0},      // TR
  {255, 192, 203},    // BL
  {0, 0, 255},        // BR
};

void solidLoad() {
  JsonArray colors = settingsMode(1)["colors"].as<JsonArray>();
  if (colors.size() >= AURORA_LED_COUNT) {
    for (int i = 0; i < AURORA_LED_COUNT; i++) {
      JsonArray c = colors[i];
      if (c.size() >= 3) {
        cornerColors[i][0] = c[0];
        cornerColors[i][1] = c[1];
        cornerColors[i][2] = c[2];
      }
    }
  }
  // fewer than 4 entries → keep current (defaults) for the missing ones
}

void solidApply() {
  for (int corner = 0; corner < AURORA_LED_COUNT; corner++) {
    softPwmSet(kCornerToLed[corner],
               cornerColors[corner][0],
               cornerColors[corner][1],
               cornerColors[corner][2]);
  }
}

void solidSetCorner(int corner, uint8_t r, uint8_t g, uint8_t b) {
  if (corner < 0 || corner >= AURORA_LED_COUNT) return;
  cornerColors[corner][0] = r;
  cornerColors[corner][1] = g;
  cornerColors[corner][2] = b;
  softPwmSet(kCornerToLed[corner], r, g, b);
}

bool solidGetCorner(int corner, uint8_t* r, uint8_t* g, uint8_t* b) {
  if (corner < 0 || corner >= AURORA_LED_COUNT) return false;
  *r = cornerColors[corner][0];
  *g = cornerColors[corner][1];
  *b = cornerColors[corner][2];
  return true;
}

void solidSave() {
  JsonDocument& doc = settings;   // settings is extern JsonDocument
  JsonArray colors = doc["MODE_1"]["colors"].to<JsonArray>();
  colors.clear();
  for (int i = 0; i < AURORA_LED_COUNT; i++) {
    JsonArray c = colors.add<JsonArray>();
    c.add(cornerColors[i][0]);
    c.add(cornerColors[i][1]);
    c.add(cornerColors[i][2]);
  }
  settingsSaveMode(1);
}

void solidOff() {
  softPwmOff();
}
