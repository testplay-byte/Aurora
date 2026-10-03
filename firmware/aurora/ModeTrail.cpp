// ============================================================================
// Aurora — ModeTrail.cpp  (mode 5)
// A comet with exponential decay travels around the 4 corners with
// sub-pixel blending between adjacent corners.
//
// Timing: one lap = `speed` ms, sampled in 20 steps → step every speed/20 ms,
// position advances 1/20 lap (= 0.2 corner units) per step.
// ============================================================================
#include "Modes.h"
#include "Config.h"
#include "Settings.h"
#include "SoftPwm.h"

static const uint8_t kCornerToLed[AURORA_LED_COUNT] = AURORA_CORNER_TO_LED;

static uint32_t speedMs = 1000;
static bool forward = true;
static int trailLength = 3;
static int brightness = 100;
static int colorCount = 1;
static uint8_t colors[8][3];

static uint32_t lastStep = 0;
static float pos = 0.0f;

void trailBegin() {
  JsonObject s = settingsMode(5);
  speedMs = s["speed"] | 1000;
  if (speedMs < 100) speedMs = 100;
  forward = s["direction"] | true;
  trailLength = s["trail_length"] | 3;
  if (trailLength < 1) trailLength = 1;
  if (trailLength > AURORA_LED_COUNT) trailLength = AURORA_LED_COUNT;
  brightness = s["brightness"] | 100;

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

  lastStep = millis();
  pos = 0.0f;
}

void trailTick(uint32_t now) {
  uint32_t step = speedMs / 20;
  if (step == 0) step = 1;
  if (now - lastStep < step) return;
  lastStep += step;

  pos += forward ? 0.2f : -0.2f;
  while (pos >= (float)AURORA_LED_COUNT) pos -= AURORA_LED_COUNT;
  while (pos < 0.0f) pos += AURORA_LED_COUNT;

  float maxBright = brightness / 100.0f;
  float level[AURORA_LED_COUNT] = {0, 0, 0, 0};

  for (int i = 0; i < trailLength; i++) {
    float p = pos - i;
    while (p < 0) p += AURORA_LED_COUNT;
    while (p >= AURORA_LED_COUNT) p -= AURORA_LED_COUNT;

    float decay = powf(0.6f, i);                 // fade per segment
    float b = maxBright * decay;
    int c1 = (int)p;
    int c2 = (c1 + 1) % AURORA_LED_COUNT;
    float blend = p - (int)p;

    level[c1] += b * (1.0f - blend);
    level[c2] += b * blend;
  }

  for (int corner = 0; corner < AURORA_LED_COUNT; corner++) {
    if (level[corner] <= 0) continue;
    if (level[corner] > maxBright) level[corner] = maxBright;
    int ci = corner % colorCount;
    float f = level[corner];
    softPwmSet(kCornerToLed[corner],
               colors[ci][0] * f, colors[ci][1] * f, colors[ci][2] * f);
  }
}

void trailEnd() {
  softPwmOff();
}
