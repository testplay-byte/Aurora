// ============================================================================
// Aurora — WebUi.cpp
//
// The v1 firmware compiled this whole subsystem under ENABLE_WIFI but never
// called it — a dead feature. In Aurora, webUiBegin() is invoked from
// setup() whenever AURORA_ENABLE_WIFI=1, so the flag is REAL: turn it on,
// flash, and the pages are served; turn it off, and the build is BLE-only.
//
// HTTP API (all JSON unless noted) — see docs/WEB-UI.md:
//   GET  /api/mode              {mode,name}
//   POST /api/mode              {"mode":n}  or form mode=n     → 200 text
//   GET  /api/settings          full settings object
//   POST /api/settings          partial object → deep-merged      → 200 text
//   GET  /api/data              [ {unix,time,date,duration,timezone}… ]
//   GET  /api/wifi/scan         [ {ssid,rssi,secure}… ]
//   POST /api/solid/led         {"corner":0-3,"r","g","b"}        → 200 text
//   POST /api/solid/save        (persist corner colors)           → 200 text
//   GET  /*                     static files from LittleFS (index.html at /)
// ============================================================================
#include "WebUi.h"
#include "Config.h"

#if AURORA_ENABLE_WIFI

#include "Settings.h"
#include "Modes.h"
#include "Solid.h"
#include "Schedule.h"
#include <WiFi.h>
#include <ESPmDNS.h>
#include <WebServer.h>
#include <LittleFS.h>

static WebServer server(80);
static bool serverUp = false;

// ---------------------------------------------------------------------------
// Wi-Fi bring-up: primary STA → secondary STA → AP fallback
// ---------------------------------------------------------------------------
static bool trySta(const char* ssid, const char* pass, int timeoutS) {
  if (!ssid || !ssid[0]) return false;
  Serial.printf("[wifi] connecting to \"%s\"", ssid);
  WiFi.begin(ssid, pass);
  for (int i = 0; i < timeoutS * 2; i++) {
    delay(500);
    Serial.print('.');
    if (WiFi.status() == WL_CONNECTED) { Serial.println(" ok"); return true; }
  }
  Serial.println(" timeout");
  WiFi.disconnect(false);
  delay(300);
  return false;
}

static void startAccessPoint(const char* ssid, const char* pass) {
  WiFi.mode(WIFI_AP);
  IPAddress ip(10, 10, 10, 10), gw(10, 10, 10, 1), mask(255, 255, 255, 0);
  WiFi.softAPConfig(ip, gw, mask);
  const char* p = (pass && strlen(pass) >= 8) ? pass : nullptr;
  bool ok = WiFi.softAP(ssid, p);
  delay(200);
  if (ok) {
    Serial.printf("[wifi] AP \"%s\" up → http://10.10.10.10%s\n",
                  ssid, p ? "" : " (open)");
  } else {
    Serial.println("[wifi] AP start FAILED");
  }
}

static void connectWiFi() {
  JsonObject g = settingsGeneral();

  WiFi.persistent(false);
  WiFi.setAutoReconnect(g["wifi_auto_reconnect"] | true);
  WiFi.mode(WIFI_STA);
  WiFi.setHostname(AURORA_HOSTNAME);

  String ssid1 = g["wifi_ssid"] | "";
  String pass1 = g["wifi_password"] | "";
  String ssid2 = g["wifi_ssid2"] | "";
  String pass2 = g["wifi_password2"] | "";

  if (trySta(ssid1.c_str(), pass1.c_str(), AURORA_STA1_TIMEOUT_S) ||
      trySta(ssid2.c_str(), pass2.c_str(), AURORA_STA2_TIMEOUT_S)) {
    Serial.printf("[wifi] connected — IP %s, http://%s.local\n",
                  WiFi.localIP().toString().c_str(), AURORA_HOSTNAME);
    return;
  }

  if (ssid1.length() == 0 && ssid2.length() == 0) {
    Serial.println("[wifi] no credentials configured — starting AP");
  } else {
    Serial.println("[wifi] all networks failed — starting AP");
  }
  startAccessPoint(g["ap_ssid"] | AURORA_AP_SSID, g["ap_password"] | AURORA_AP_PASSWORD);
}

// ---------------------------------------------------------------------------
// Handlers
// ---------------------------------------------------------------------------
static void sendText(int code, const String& body) {
  server.send(code, "text/plain", body);
}

static void handleGetMode() {
  JsonDocument doc;
  doc["mode"] = modeActive();
  doc["name"] = modeName(modeActive());
  String out;
  serializeJson(doc, out);
  server.send(200, "application/json", out);
}

static void handleSetMode() {
  int m = -1;
  if (server.hasArg("mode")) {
    m = server.arg("mode").toInt();                       // form style
  } else {
    JsonDocument doc;
    if (!deserializeJson(doc, server.arg("plain")) && doc["mode"].is<int>()) {
      m = doc["mode"];                                    // JSON style
    }
  }
  if (m < 0 || m >= AURORA_MODE_COUNT) {
    sendText(400, "Invalid mode");
    return;
  }
  modeSet(m);
  sendText(200, "Mode set to " + String(m));
}

static void handleGetSettings() {
  String out;
  serializeJson(settings, out);
  server.send(200, "application/json", out);
}

static void handleSetSettings() {
  JsonDocument doc;
  if (deserializeJson(doc, server.arg("plain"))) {
    sendText(400, "Invalid JSON");
    return;
  }
  // Deep merge: pages send only their own block (e.g. {"MODE_2":{…}}),
  // so concurrent edits of other blocks are not clobbered.
  settingsMerge(settings.as<JsonObject>(), doc.as<JsonObjectConst>());
  settingsSave();
  sendText(200, "Settings merged and saved");
}

static void handleGetData() {
  if (!LittleFS.exists("/data.json")) {
    server.send(200, "application/json", "[]");
    return;
  }
  File f = LittleFS.open("/data.json", "r");
  server.streamFile(f, "application/json");
  f.close();
}

static void handleWifiScan() {
  int n = WiFi.scanNetworks();
  JsonDocument doc;
  JsonArray arr = doc.to<JsonArray>();
  for (int i = 0; i < n; i++) {
    JsonObject net = arr.add<JsonObject>();
    net["ssid"] = WiFi.SSID(i);
    net["rssi"] = WiFi.RSSI(i);
    net["secure"] = (WiFi.encryptionType(i) != WIFI_AUTH_OPEN);
  }
  WiFi.scanDelete();
  String out;
  serializeJson(doc, out);
  server.send(200, "application/json", out);
}

static void handleSolidLed() {
  JsonDocument doc;
  if (deserializeJson(doc, server.arg("plain"))) {
    sendText(400, "Invalid JSON");
    return;
  }
  int corner = doc["corner"] | -1;
  int r = doc["r"] | -1, g = doc["g"] | -1, b = doc["b"] | -1;
  if (corner < 0 || corner >= AURORA_LED_COUNT ||
      r < 0 || r > 255 || g < 0 || g > 255 || b < 0 || b > 255) {
    sendText(400, "Invalid parameters");
    return;
  }
  solidSetCorner(corner, r, g, b);
  sendText(200, "OK");
}

static void handleSolidSave() {
  solidSave();
  sendText(200, "Saved");
}

// ---------------------------------------------------------------------------
// Routes — explicit handlers FIRST, static files LAST (first match wins)
// ---------------------------------------------------------------------------
static void mountRoutes() {
  server.enableCORS(true);

  server.on("/", HTTP_GET, []() {
    File f = LittleFS.open("/index.html", "r");
    if (!f) { sendText(404, "index.html missing — upload the LittleFS image"); return; }
    server.streamFile(f, "text/html");
    f.close();
  });

  server.on("/api/mode", HTTP_GET, handleGetMode);
  server.on("/api/mode", HTTP_POST, handleSetMode);
  server.on("/api/settings", HTTP_GET, handleGetSettings);
  server.on("/api/settings", HTTP_POST, handleSetSettings);
  server.on("/api/data", HTTP_GET, handleGetData);
  server.on("/api/wifi/scan", HTTP_GET, handleWifiScan);
  server.on("/api/solid/led", HTTP_POST, handleSolidLed);
  server.on("/api/solid/save", HTTP_POST, handleSolidSave);

  // Static assets from LittleFS: any GET not claimed above
  server.serveStatic("/", LittleFS, "/");

  server.onNotFound([]() {
    if (server.method() == HTTP_OPTIONS) {         // CORS preflight
      server.sendHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
      server.sendHeader("Access-Control-Allow-Headers", "Content-Type");
      server.send(204);
      return;
    }
    sendText(404, "Not found: " + server.uri());
  });
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------
void webUiBegin() {
  Serial.println("[wifi] bringing up network + web UI…");
  // LittleFS is already mounted by setup() (both builds share the history log)

  connectWiFi();
  mountRoutes();
  server.begin();
  serverUp = true;

  // mDNS upkeep is handled in webUiLoop() so reconnects re-register cleanly.
  Serial.println("[wifi] HTTP server started (80)");
}

void webUiLoop() {
  if (!serverUp) return;
  server.handleClient();

  // mDNS follows the STA link: register on connect, drop on disconnect
  static bool mdnsUp = false;
  bool up = (WiFi.status() == WL_CONNECTED);
  if (up && !mdnsUp) {
    MDNS.end();
    if (MDNS.begin(AURORA_HOSTNAME)) {
      MDNS.addService("http", "tcp", 80);
      Serial.println("[wifi] mDNS → http://" AURORA_HOSTNAME ".local");
    }
    scheduleSyncTime();          // (re)start NTP whenever the link returns
    mdnsUp = true;
  } else if (!up && mdnsUp) {
    MDNS.end();
    mdnsUp = false;
  }
}

bool webUiActive() {
  return serverUp;
}

#else  // ------------------------------------------------------------------
// BLE-only build: everything below is compiled out entirely.

void webUiBegin() {}
void webUiLoop() {}
bool webUiActive() { return false; }

#endif  // AURORA_ENABLE_WIFI
