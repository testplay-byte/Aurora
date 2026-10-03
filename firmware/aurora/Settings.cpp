// ============================================================================
// Aurora — Settings.cpp
// ============================================================================
#include "Settings.h"
#include "Config.h"

JsonDocument settings;
static Preferences prefs;
static bool loaded = false;

// --- palette helpers (used by settingsCreateDefaults) ---
static void addPalette(JsonArray arr, int n) {
  static const uint8_t pal[4][3] = {{255,0,0},{0,255,0},{0,0,255},{255,255,0}};
  for (int i = 0; i < n; i++) {
    JsonArray c = arr.add<JsonArray>();
    c.add(pal[i][0]); c.add(pal[i][1]); c.add(pal[i][2]);
  }
}
static void addTrailPalette(JsonArray arr) {
  JsonArray c = arr.add<JsonArray>();
  c.add(0); c.add(255); c.add(0);
}
static void addSpiralPalette(JsonArray arr) {
  static const uint8_t pal[4][3] = {{255,0,0},{0,255,0},{0,0,255},{255,255,0}};
  for (int i = 0; i < 4; i++) {
    JsonArray c = arr.add<JsonArray>();
    c.add(pal[i][0]); c.add(pal[i][1]); c.add(pal[i][2]);
  }
}

// ---------------------------------------------------------------------------
// Defaults — deliberately credential-free (real Wi-Fi details live only in
// the device's NVS or in the user's head, never in this repository).
// ---------------------------------------------------------------------------
void settingsCreateDefaults() {
  settings.clear();

  JsonObject general = settings["GENERAL"].to<JsonObject>();
  general["red_limit_enabled"] = false;
  general["red_limit_value"] = 255;
  general["auto_turn_on_enabled"] = false;
  general["auto_turn_on_time"] = "18:00";
  general["auto_turn_off_enabled"] = false;
  general["auto_turn_off_time"] = "23:00";
  general["short_blink_count"] = 3;
  general["short_blink_speed"] = 200;
  general["short_alert_brightness"] = 128;
  general["wifi_ssid"] = "";
  general["wifi_password"] = "";
  general["wifi_ssid2"] = "";
  general["wifi_password2"] = "";
  general["ap_ssid"] = AURORA_AP_SSID;
  general["ap_password"] = AURORA_AP_PASSWORD;
  general["wifi_auto_reconnect"] = true;
  general["save_mode_on_power_loss"] = true;
  general["debug_logging"] = false;

  // MODE_0 — OFF
  settings["MODE_0"]["name"] = "OFF";

  // MODE_1 — SOLID: one color per corner (TL, TR, BL, BR)
  JsonArray colors = settings["MODE_1"]["colors"].to<JsonArray>();
  const uint8_t defaults[4][3] = {
    {255, 0, 0},       // TL — red
    {255, 255, 0},     // TR — yellow
    {255, 192, 203},   // BL — pink
    {0, 0, 255}        // BR — blue
  };
  settings["MODE_1"]["name"] = "SOLID";
  for (int i = 0; i < 4; i++) {
    JsonArray c = colors.add<JsonArray>();
    c.add(defaults[i][0]);
    c.add(defaults[i][1]);
    c.add(defaults[i][2]);
  }

  // MODE_2 — BLINK
  JsonObject m2 = settings["MODE_2"].to<JsonObject>();
  m2["name"] = "BLINK";
  m2["interval"] = 500;
  m2["random"] = true;
  m2["no_off"] = false;

  // MODE_3 — WAVE
  JsonObject m3 = settings["MODE_3"].to<JsonObject>();
  m3["name"] = "WAVE";
  m3["interval"] = 50;
  m3["pattern"] = "diagonal";

  // MODE_4 — GLOW
  JsonObject m4 = settings["MODE_4"].to<JsonObject>();
  m4["name"] = "GLOW";
  m4["interval"] = 30;
  m4["smooth_transitions"] = false;
  m4["breathing_effect"] = true;

  // MODE_5 — TRAIL
  JsonObject m5 = settings["MODE_5"].to<JsonObject>();
  m5["name"] = "TRAIL";
  m5["speed"] = 1000;
  m5["direction"] = true;
  m5["trail_length"] = 3;
  m5["brightness"] = 100;

  // MODE_6 — SPIRAL
  JsonObject m6 = settings["MODE_6"].to<JsonObject>();
  m6["name"] = "SPIRAL";
  m6["speed"] = 1000;
  m6["direction"] = true;
  m6["brightness"] = 100;
  m6["paused"] = false;

  // Shared palettes: BLINK/WAVE/GLOW get 4 colors, TRAIL 1, SPIRAL exactly 4.
  addPalette(settings["MODE_2"]["colors"], 4);
  addPalette(settings["MODE_3"]["colors"], 4);
  addPalette(settings["MODE_4"]["colors"], 4);
  addTrailPalette(settings["MODE_5"]["colors"]);                 // green
  addSpiralPalette(settings["MODE_6"]["colors"]);                // R,G,B,Y
}

// ---------------------------------------------------------------------------
// NVS I/O
// ---------------------------------------------------------------------------
static bool loadNamespace(const char* ns) {
  // Returns true if at least one block was found in `ns`.
  bool any = false;
  for (int i = 0; i <= 6; i++) {
    String key = "mode" + String(i);
    String json = prefs.getString(key.c_str(), "");
    if (json.length() > 5) {
      JsonDocument doc;
      if (!deserializeJson(doc, json)) {
        settings["MODE_" + String(i)] = doc;
        any = true;
      } else {
        Serial.printf("[settings] %s/%s parse error\n", ns, key.c_str());
      }
    }
  }
  String gen = prefs.getString("general", "");
  if (gen.length() > 5) {
    JsonDocument doc;
    if (!deserializeJson(doc, gen)) {
      settings["GENERAL"] = doc;
      any = true;
    }
  }
  return any;
}

static void saveNamespaceTo(Preferences& target) {
  for (int i = 0; i <= 6; i++) {
    String modeKey = "MODE_" + String(i);
    if (settings.containsKey(modeKey)) {
      String json;
      serializeJson(settings[modeKey], json);
      target.putString(("mode" + String(i)).c_str(), json);
    }
  }
  if (settings.containsKey("GENERAL")) {
    String json;
    serializeJson(settings["GENERAL"], json);
    target.putString("general", json);
  }
}

void settingsLoad() {
  prefs.begin(AURORA_NVS_NAMESPACE, false);
  settings.clear();

  bool fresh = !loadNamespace(AURORA_NVS_NAMESPACE);

  if (fresh) {
    // One-time migration from the v1 firmware's namespace — keeps the user's
    // tuned modes, schedules and (device-side) Wi-Fi details across the upgrade.
    Preferences legacy;
    if (legacy.begin(AURORA_LEGACY_NAMESPACE, true)) {
      bool hadLegacy = loadNamespace(AURORA_LEGACY_NAMESPACE);
      legacy.end();
      if (hadLegacy) {
        saveNamespaceTo(prefs);
        Serial.println("[settings] migrated v1 ('ntlwc') settings into 'aurora'");
      }
    }
  }

  if (!settings.as<JsonObject>().size()) {
    Serial.println("[settings] no settings found — installing defaults");
    settingsCreateDefaults();
    saveNamespaceTo(prefs);
  }

  loaded = true;
}

void settingsSave() {
  if (!loaded) return;
  saveNamespaceTo(prefs);
}

void settingsSaveMode(int mode) {
  if (!loaded || mode < 0 || mode > 6) return;
  String modeKey = "MODE_" + String(mode);
  if (!settings.containsKey(modeKey)) return;
  String json;
  serializeJson(settings[modeKey], json);
  prefs.putString(("mode" + String(mode)).c_str(), json);
}

// ---------------------------------------------------------------------------
// Accessors
// ---------------------------------------------------------------------------
JsonObject settingsMode(int mode) {
  return settings["MODE_" + String(mode)].as<JsonObject>();
}

JsonObject settingsGeneral() {
  return settings["GENERAL"].as<JsonObject>();
}

int settingsLastMode() {
  return prefs.getInt("lastMode", 0);
}
void settingsSetLastMode(int mode) {
  prefs.putInt("lastMode", mode);
}
int settingsLastActiveMode() {
  return prefs.getInt("lastActiveMode", 1);
}
void settingsSetLastActiveMode(int mode) {
  prefs.putInt("lastActiveMode", mode);
}

// ---------------------------------------------------------------------------
// Deep merge — objects merge, scalars/arrays replace.
// ---------------------------------------------------------------------------
void settingsMerge(JsonObject target, JsonObjectConst patch) {
  for (JsonPairConst kv : patch) {
    if (kv.value().is<JsonObjectConst>() && target[kv.key()].is<JsonObject>()) {
      settingsMerge(target[kv.key()].as<JsonObject>(), kv.value().as<JsonObjectConst>());
    } else {
      target[kv.key()] = kv.value();
    }
  }
}
