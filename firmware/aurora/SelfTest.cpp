// ============================================================================
// Aurora — SelfTest.cpp
// Boot animation: sweeps R → G → B around the corners (TL→TR→BR→BL),
// then flashes all-red, all-green, all-blue, white, then off.
// If the box does this on power-up, the PWM wiring is alive.
// ============================================================================
#include "Config.h"
#include "SoftPwm.h"
#include <Arduino.h>

static const uint8_t kCornerToLed[AURORA_LED_COUNT] = AURORA_CORNER_TO_LED;

static void sweep(uint8_t r, uint8_t g, uint8_t b, uint32_t onMs) {
  for (int i = 0; i < AURORA_LED_COUNT; i++) {
    softPwmOff();
    softPwmSet(kCornerToLed[i], r, g, b);
    delay(onMs);
  }
}

static void allFor(uint8_t r, uint8_t g, uint8_t b, uint32_t ms) {
  softPwmSetAll(r, g, b);
  delay(ms);
}

void selfTestRun() {
  const uint32_t on = 200;
  const uint32_t gap = 60;

  sweep(255, 0, 0, on);      delay(gap);
  sweep(0, 255, 0, on);      delay(gap);
  sweep(0, 0, 255, on);      delay(gap);

  allFor(255, 0, 0, on * 2);
  allFor(0, 255, 0, on * 2);
  allFor(0, 0, 255, on * 2);
  allFor(255, 255, 255, on * 2);

  softPwmOff();
  delay(100);
}
