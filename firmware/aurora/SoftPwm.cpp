// ============================================================================
// Aurora — SoftPwm.cpp
//
// A hardware timer ticks at 20 kHz; each tick walks all 12 channels and
// drives the pin HIGH while its 8-bit counter is below its target value.
// Modes never touch GPIOs directly — they only write pwmValues[][].
// ============================================================================
#include "SoftPwm.h"
#include "Config.h"
#include "Settings.h"

static const uint8_t kLedPins[AURORA_LED_COUNT][AURORA_CHANNELS] = AURORA_LED_PINS;

static uint8_t pwmValues[AURORA_LED_COUNT][AURORA_CHANNELS] = {0};
// Written by the ISR task context only after softPwmBegin — guarded by
// the fact that 8-bit stores are atomic on Xtensa and channels only move
// one byte at a time.
static volatile uint8_t pwmCounters[AURORA_LED_COUNT][AURORA_CHANNELS] = {0};

static hw_timer_t* pwmTimer = nullptr;

static void IRAM_ATTR onTimer() {
  for (int led = 0; led < AURORA_LED_COUNT; led++) {
    for (int ch = 0; ch < AURORA_CHANNELS; ch++) {
      digitalWrite(kLedPins[led][ch], pwmCounters[led][ch] < pwmValues[led][ch]);
      pwmCounters[led][ch]++;
    }
  }
}

void softPwmBegin() {
  for (int led = 0; led < AURORA_LED_COUNT; led++) {
    for (int ch = 0; ch < AURORA_CHANNELS; ch++) {
      pinMode(kLedPins[led][ch], OUTPUT);
      digitalWrite(kLedPins[led][ch], LOW);
    }
  }

  // ESP32 Arduino core 3.x timer API
  pwmTimer = timerBegin(AURORA_PWM_FREQUENCY_HZ);
  timerAttachInterrupt(pwmTimer, &onTimer);
  timerAlarm(pwmTimer, 1, true, 0);  // fire every tick, auto-reload

  Serial.printf("[pwm] %d kHz software PWM on %d channels\n",
                AURORA_PWM_FREQUENCY_HZ / 1000, AURORA_LED_COUNT * AURORA_CHANNELS);
}

void softPwmSet(int led, uint8_t r, uint8_t g, uint8_t b) {
  if (led < 0 || led >= AURORA_LED_COUNT) return;

  // Global red limit (heat/power protection) — applies to every mode.
  // limit 0 = red channel fully off (same semantics as v1).
  JsonObject general = settingsGeneral();
  if (general["red_limit_enabled"].as<bool>()) {
    uint8_t limit = general["red_limit_value"].as<uint8_t>();
    if (r > limit) r = limit;
  }

  pwmValues[led][0] = r;
  pwmValues[led][1] = g;
  pwmValues[led][2] = b;
}

void softPwmSetAll(uint8_t r, uint8_t g, uint8_t b, uint8_t brightness) {
  r = (uint16_t)r * brightness / 255;
  g = (uint16_t)g * brightness / 255;
  b = (uint16_t)b * brightness / 255;
  for (int led = 0; led < AURORA_LED_COUNT; led++) {
    softPwmSet(led, r, g, b);
  }
}

void softPwmOff() {
  for (int led = 0; led < AURORA_LED_COUNT; led++) {
    for (int ch = 0; ch < AURORA_CHANNELS; ch++) {
      pwmValues[led][ch] = 0;
    }
  }
}
