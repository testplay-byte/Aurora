// ============================================================================
// Aurora — Modes.cpp
// ============================================================================
#include "Modes.h"
#include "Config.h"
#include "Settings.h"

const AuroraMode kModes[AURORA_MODE_COUNT] = {
  {"OFF",    offBegin,   offTick,   offEnd},
  {"SOLID",  solidBegin, solidTick, solidEnd},
  {"BLINK",  blinkBegin, blinkTick, blinkEnd},
  {"WAVE",   waveBegin,  waveTick,  waveEnd},
  {"GLOW",   glowBegin,  glowTick,  glowEnd},
  {"TRAIL",  trailBegin, trailTick, trailEnd},
  {"SPIRAL", spiralBegin, spiralTick, spiralEnd},
};

static int current = 0;

void modesInit() {
  for (int i = 0; i < AURORA_MODE_COUNT; i++) {
    Serial.printf("[modes] %d = %s\n", i, kModes[i].name);
  }
}

void modeSet(int m) {
  if (m < 0 || m >= AURORA_MODE_COUNT || m == current) return;

  kModes[current].end();
  current = m;
  kModes[current].begin();

  settingsSetLastMode(m);
  Serial.printf("[modes] active -> %d %s\n", m, kModes[current].name);
}

void modeTick(uint32_t nowMs) {
  kModes[current].tick(nowMs);
}

int modeActive() {
  return current;
}

const char* modeName(int m) {
  if (m < 0 || m >= AURORA_MODE_COUNT) return "?";
  const char* n = settingsMode(m)["name"].as<const char*>();
  return (n && n[0]) ? n : kModes[m].name;
}
