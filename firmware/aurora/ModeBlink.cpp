// ============================================================================
// Aurora — ModeBlink.cpp  (mode 2)
// On/off blinking with an optional palette cycle ("no_off" = never dark).
// Settings are cached once at begin() — restart the mode to apply changes.
// ============================================================================
#include "Modes.h"
#include "Settings.h"
#include "SoftPwm.h"

static uint32_t interval = 500;      // ms between phase changes
static bool noOff = false;           // true: cycle colors without going dark
static int colorCount = 1;
static uint8_t colors[8][3];

static uint32_t lastToggle = 0;
static bool on = false;
static int idx = 0;

void blinkBegin() {
  JsonObject s = settingsMode(2);
  interval = s["interval"] | 500;
  if (interval < 10) interval = 10;
  noOff = s["no_off"] | false;

  JsonArray arr = s["colors"];
  colorCount = arr.size();
  if (colorCount > 8) colorCount = 8;
  if (colorCount < 1) colorCount = 1;
  for (int i = 0; i < colorCount; i++) {
    JsonArray c = arr[i];
    colors[i][0] = c[0] | 0;
    colors[i][1] = c[1] | 0;
    colors[i][2] = c[2] | 0;
  }

  lastToggle = millis();
  on = true;
  idx = 0;
  softPwmSetAll(colors[0][0], colors[0][1], colors[0][2]);
}

void blinkTick(uint32_t now) {
  if (now - lastToggle < interval) return;
  lastToggle += interval;   // catch-up keeps timing drift-free

  if (noOff) {
    idx = (idx + 1) % colorCount;
    softPwmSetAll(colors[idx][0], colors[idx][1], colors[idx][2]);
    on = true;
    return;
  }

  if (on) {
    softPwmOff();
    idx = (idx + 1) % colorCount;   // pick the next color while dark
    on = false;
  } else {
    softPwmSetAll(colors[idx][0], colors[idx][1], colors[idx][2]);
    on = true;
  }
}

void blinkEnd() {
  softPwmOff();
}
