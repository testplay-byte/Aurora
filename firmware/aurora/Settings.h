// ============================================================================
// Aurora — Settings.h
// Owns the global settings document (GENERAL + MODE_0..MODE_6),
// persists it to NVS, and migrates v1 ("ntlwc") settings on first boot.
// ============================================================================
#ifndef AURORA_SETTINGS_H
#define AURORA_SETTINGS_H

#include <Arduino.h>
#include <ArduinoJson.h>
#include <Preferences.h>

// The live settings document. Layout:
//   { "GENERAL": {...}, "MODE_0": {...}, ... "MODE_6": {...} }
extern JsonDocument settings;

// Load from NVS (namespace "aurora"); if empty, migrate from the legacy
// "ntlwc" namespace; if that is empty too, install compiled defaults.
void settingsLoad();

// Persist everything to NVS.
void settingsSave();

// Persist a single mode block (cheap, used by BLE SET:MODESETTINGS).
void settingsSaveMode(int mode);

// Accessors (return null-object JsonObjects if the key is missing).
JsonObject settingsMode(int mode);
JsonObject settingsGeneral();

// Boot-mode bookkeeping (small ints kept outside the JSON doc).
int  settingsLastMode();               // restored on boot (default 0)
void settingsSetLastMode(int mode);
int  settingsLastActiveMode();         // used by auto-on (default 1)
void settingsSetLastActiveMode(int mode);

// Compiled defaults for a fresh device (no credentials, ever).
void settingsCreateDefaults();

// Deep-merge `patch` into `target`: objects merge recursively, everything
// else is replaced. Used by POST /api/settings so pages can send just their
// own block without clobbering concurrent edits.
void settingsMerge(JsonObject target, JsonObjectConst patch);

#endif  // AURORA_SETTINGS_H
