// ============================================================================
// Aurora — SoftPwm.h
// 20 kHz software PWM for the 12 LED channels (4 RGB corners).
// ============================================================================
#ifndef AURORA_SOFTPWM_H
#define AURORA_SOFTPWM_H

#include <Arduino.h>

// Start the PWM timer (call once from setup()).
void softPwmBegin();

// Set one LED's color (0..LED_COUNT-1, physical LED index).
// Applies the GLOBAL red-limit from GENERAL settings when enabled.
void softPwmSet(int led, uint8_t r, uint8_t g, uint8_t b);

// Set every LED to one color, scaled by brightness (0-255).
void softPwmSetAll(uint8_t r, uint8_t g, uint8_t b, uint8_t brightness = 255);

// All channels to 0.
void softPwmOff();

#endif  // AURORA_SOFTPWM_H
