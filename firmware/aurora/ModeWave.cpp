// ============================================================================
// Aurora — ModeWave.cpp  (mode 3)
// Two diagonal corner groups fade against each other on a sine phase.
// When a group crosses zero it draws a new palette color (≠ the other group).
// Group A = TL+BR · Group B = TR+BL
// ============================================================================
#include "Modes.h"
#include "Config.h"
#include "Settings.h"
#include "SoftPwm.h"
#include <math.h>

static const uint8_t kCornerToLed[AURORA_LED_COUNT] = AURORA_CORNER_TO_LED;
static const int kGroupA[2] = {1, 3};   // corners TL, BR
static const int kGroupB[2] = {0, 2};   // corners TR, BL

static uint32_t stepMs = 50;
static int colorCount = 4;
static uint8_t colors[8][3];

static uint32_t lastStep = 0;
static float phase = 0.0f;
static int colorA = 0, colorB = 1;
static bool swappedForA = false, swappedForB = false;

void waveBegin() {
  JsonObject s = settingsMode(3);
  stepMs = s["interval"] | 50;
  if (stepMs < 5) stepMs = 5;

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
  colorA = 0;
  colorB = (colorCount > 1) ? 1 : 0;
  swappedForA = swappedForB = false;
}

static void pickNewColor(int& slot, int avoid) {
  if (colorCount < 2) return;
  int next;
  do {
    next = random(colorCount);
  } while (next == avoid);
  slot = next;
}

void waveTick(uint32_t now) {
  if (now - lastStep < stepMs) return;
  lastStep += stepMs;

  phase += 0.1f;
  if (phase >= 2.0f * PI) phase -= 2.0f * PI;

  float brightA = (sinf(phase) + 1.0f) / 2.0f;
  float brightB = 1.0f - brightA;

  // each group gets a fresh color whenever it sits at its dark extreme
  if (brightA <= 0.01f && !swappedForA) { pickNewColor(colorA, colorB); swappedForA = true;  swappedForB = false; }
  if (brightB <= 0.01f && !swappedForB) { pickNewColor(colorB, colorA); swappedForB = true;  swappedForA = false; }
  if (brightA > 0.1f) swappedForA = false;
  if (brightB > 0.1f) swappedForB = false;

  for (int i = 0; i < 2; i++) {
    softPwmSet(kCornerToLed[kGroupA[i]],
               colors[colorA][0] * brightA, colors[colorA][1] * brightA, colors[colorA][2] * brightA);
    softPwmSet(kCornerToLed[kGroupB[i]],
               colors[colorB][0] * brightB, colors[colorB][1] * brightB, colors[colorB][2] * brightB);
  }
}

void waveEnd() {
  softPwmOff();
}
