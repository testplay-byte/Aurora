// ============================================================================
// Aurora — ModeSpiral.cpp  (mode 6)
// The 4 palette colors rotate through the 4 corners frame by frame.
// Exactly 4 colors; `speed` = ms per frame; `paused` freezes the animation.
// ============================================================================
#include "Modes.h"
#include "Config.h"
#include "Settings.h"
#include "SoftPwm.h"

static const uint8_t kCornerToLed[AURORA_LED_COUNT] = AURORA_CORNER_TO_LED;

static uint32_t speedMs = 1000;
static bool forward = true;
static int brightness = 100;
static bool paused = false;
static uint8_t colors[4][3];

static uint32_t lastStep = 0;
static int frame = 0;

void spiralBegin() {
  JsonObject s = settingsMode(6);
  speedMs = s["speed"] | 1000;
  if (speedMs < 50) speedMs = 50;
  forward = s["direction"] | true;
  brightness = s["brightness"] | 100;
  paused = s["paused"] | false;

  JsonArray arr = s["colors"];
  for (int i = 0; i < 4; i++) {
    if (i < (int)arr.size() && arr[i].size() >= 3) {
      colors[i][0] = arr[i][0];
      colors[i][1] = arr[i][1];
      colors[i][2] = arr[i][2];
    }
  }

  lastStep = millis();
  frame = 0;
}

void spiralTick(uint32_t now) {
  if (paused) return;
  if (now - lastStep < speedMs) return;
  lastStep += speedMs;

  // which palette index sits on each corner (TL, TR, BL, BR) this frame
  static const uint8_t kFrames[4][4] = {
    {0, 1, 3, 2},
    {3, 0, 2, 1},
    {2, 3, 1, 0},
    {1, 2, 0, 3},
  };

  float f = brightness / 100.0f;
  for (int corner = 0; corner < AURORA_LED_COUNT; corner++) {
    const uint8_t* c = colors[kFrames[frame][corner]];
    softPwmSet(kCornerToLed[corner], c[0] * f, c[1] * f, c[2] * f);
  }

  frame = forward ? (frame + 1) % 4 : (frame + 3) % 4;
}

void spiralEnd() {
  softPwmOff();
}
