// ============================================================================
// Aurora — ModeGlow.cpp  (mode 4)
// Breathing brightness, with optional smooth cross-fades between palette
// colors (smooth_transitions) or hard swaps at the dark trough (default).
// ============================================================================
#include "Modes.h"
#include "Settings.h"
#include "SoftPwm.h"
#include <math.h>

static uint32_t stepMs = 30;
static bool smooth = false;
static bool breathing = true;
static int colorCount = 4;
static uint8_t colors[8][3];

static uint32_t lastStep = 0;
static float phase = 0.0f;
static int cur = 0, target = 1;
static float progress = 0.0f;

void glowBegin() {
  JsonObject s = settingsMode(4);
  stepMs = s["interval"] | 30;
  if (stepMs < 5) stepMs = 5;
  smooth = s["smooth_transitions"] | false;
  breathing = s["breathing_effect"] | true;

  JsonArray arr = s["colors"];
  colorCount = arr.size();
  if (colorCount > 8) colorCount = 8;
  if (colorCount < 2) colorCount = 2;
  for (int i = 0; i < colorCount; i++) {
    JsonArray c = arr[i];
    colors[i][0] = c[0] | 0;
    colors[i][1] = c[1] | 0;
    colors[i][2] = c[2] | 0;
  }

  lastStep = millis();
  phase = 0.0f;
  progress = 0.0f;
  cur = 0;
  target = 1;
}

void glowTick(uint32_t now) {
  if (now - lastStep < stepMs) return;
  lastStep += stepMs;

  phase += 0.05f;
  if (phase >= 2.0f * PI) phase -= 2.0f * PI;
  float bright = breathing ? (sinf(phase) + 1.0f) / 2.0f : 1.0f;

  if (smooth && colorCount >= 2) {
    progress += 0.01f;
    if (progress >= 1.0f) {
      progress = 0.0f;
      cur = target;
      target = (target + 1) % colorCount;
    }
    float r = colors[cur][0] + (colors[target][0] - colors[cur][0]) * progress;
    float g = colors[cur][1] + (colors[target][1] - colors[cur][1]) * progress;
    float b = colors[cur][2] + (colors[target][2] - colors[cur][2]) * progress;
    softPwmSetAll(r * bright, g * bright, b * bright);
  } else {
    if (bright <= 0.05f) cur = (cur + 1) % colorCount;   // swap while dark
    softPwmSetAll(colors[cur][0] * bright,
                  colors[cur][1] * bright,
                  colors[cur][2] * bright);
  }
}

void glowEnd() {
  softPwmOff();
}
