// ============================================================================
// Aurora — Modes.h
// Uniform mode interface: every mode exposes begin/tick/end.
// Mode files: ModeOff / ModeSolid / ModeBlink / ModeWave / ModeGlow /
//             ModeTrail / ModeSpiral
// ============================================================================
#ifndef AURORA_MODES_H
#define AURORA_MODES_H

#include <Arduino.h>

#define AURORA_MODE_COUNT 7
//  0 OFF · 1 SOLID · 2 BLINK · 3 WAVE · 4 GLOW · 5 TRAIL · 6 SPIRAL

struct AuroraMode {
  const char* name;                       // matches settings MODE_n.name
  void (*begin)();                         // load settings, reset state, light up
  void (*tick)(uint32_t nowMs);            // called every loop while active
  void (*end)();                           // stop output (usually: all off)
};

extern const AuroraMode kModes[AURORA_MODE_COUNT];

// One-time initialization of every mode (cheap: most just log).
void modesInit();

// Switch to `m` (0..6): ends the current mode, begins the new one,
// and persists it as the boot mode.
void modeSet(int m);

// Tick the active mode (caller decides gating, e.g. during short alerts).
void modeTick(uint32_t nowMs);

int modeActive();
const char* modeName(int m);   // settings-backed name, falls back to table

// --- implemented by each ModeX.cpp ---
void offBegin();   void offTick(uint32_t now);   void offEnd();
void solidBegin(); void solidTick(uint32_t now); void solidEnd();
void blinkBegin(); void blinkTick(uint32_t now); void blinkEnd();
void waveBegin();  void waveTick(uint32_t now);  void waveEnd();
void glowBegin();  void glowTick(uint32_t now);  void glowEnd();
void trailBegin(); void trailTick(uint32_t now); void trailEnd();
void spiralBegin();void spiralTick(uint32_t now);void spiralEnd();

#endif  // AURORA_MODES_H
