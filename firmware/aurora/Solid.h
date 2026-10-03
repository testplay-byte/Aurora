// ============================================================================
// Aurora — Solid.h
// Persistent 4-corner color editor (SOLID mode + live LED: commands).
// Colors are stored in CORNER order (TL, TR, BL, BR) — the physical LED
// mapping happens through the single table in Config.h.
// ============================================================================
#ifndef AURORA_SOLID_H
#define AURORA_SOLID_H

#include <Arduino.h>
#include <ArduinoJson.h>

// Load corner colors from settings MODE_1 (falls back to built-in defaults).
void solidLoad();

// Push the stored colors to the physical LEDs.
void solidApply();

// Set one corner (0-3) and show it immediately.
void solidSetCorner(int corner, uint8_t r, uint8_t g, uint8_t b);

// Read one corner (0-3). Returns false when out of range.
bool solidGetCorner(int corner, uint8_t* r, uint8_t* g, uint8_t* b);

// Persist current corner colors into settings MODE_1 + NVS.
void solidSave();

// All corners off.
void solidOff();

#endif  // AURORA_SOLID_H
