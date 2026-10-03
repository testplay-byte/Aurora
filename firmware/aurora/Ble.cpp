// ============================================================================
// Aurora — Ble.cpp
//
// ASCII command channel: newline-terminated, colon-separated fields, written
// to the command characteristic (…beb5) and answered on the notify
// characteristic (…beb6). Same 12-command contract as v1 — see
// docs/BLE-PROTOCOL.md for the full table and JSON schemas.
// ============================================================================
#include "Ble.h"
#include "Config.h"
#include "Settings.h"
#include "Modes.h"
#include "Solid.h"
#include "SoftPwm.h"
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

static BLEServer* server = nullptr;
static BLECharacteristic* responseChar = nullptr;
static bool connected = false;
static bool wasConnected = false;
static String cmdBuffer;

// --- command helpers -------------------------------------------------------

static void handleCommand(String cmd) {
  Serial.println("[ble] <= " + cmd);

  if (cmd == "GET:MODE") {
    bleRespond("MODE:" + String(modeActive()) + ":" + String(modeName(modeActive())));

  } else if (cmd.startsWith("SET:MODE:")) {
    int m = cmd.substring(9).toInt();
    if (m >= 0 && m < AURORA_MODE_COUNT) {
      modeSet(m);
      bleRespond("OK:MODE:" + String(m) + ":" + String(modeName(m)));
    } else {
      bleRespond("ERR:INVALID_MODE");
    }

  } else if (cmd == "OFF") {
    modeSet(0);
    bleRespond("OK:OFF");

  } else if (cmd.startsWith("LED:")) {
    // LED:<corner>:<r>,<g>,<b>
    int c2 = cmd.indexOf(':', 4);
    if (c2 <= 0) { bleRespond("ERR:INVALID_FORMAT"); return; }
    int corner = cmd.substring(4, c2).toInt();
    String rgb = cmd.substring(c2 + 1);
    int g1 = rgb.indexOf(','), g2 = rgb.indexOf(',', g1 + 1);
    if (g1 <= 0 || g2 <= 0) { bleRespond("ERR:INVALID_COLOR"); return; }
    int r = rgb.substring(0, g1).toInt();
    int g = rgb.substring(g1 + 1, g2).toInt();
    int b = rgb.substring(g2 + 1).toInt();
    if (corner < 0 || corner >= AURORA_LED_COUNT ||
        r < 0 || r > 255 || g < 0 || g > 255 || b < 0 || b > 255) {
      bleRespond("ERR:INVALID_PARAMS");
      return;
    }
    solidSetCorner(corner, r, g, b);
    bleRespond("OK:LED:" + String(corner) + ":" + String(r) + "," + String(g) + "," + String(b));

  } else if (cmd.startsWith("ALL:")) {
    String rgb = cmd.substring(4);
    int g1 = rgb.indexOf(','), g2 = rgb.indexOf(',', g1 + 1);
    if (g1 <= 0 || g2 <= 0) { bleRespond("ERR:INVALID_COLOR"); return; }
    int r = rgb.substring(0, g1).toInt();
    int g = rgb.substring(g1 + 1, g2).toInt();
    int b = rgb.substring(g2 + 1).toInt();
    if (r < 0 || r > 255 || g < 0 || g > 255 || b < 0 || b > 255) {
      bleRespond("ERR:INVALID_COLOR");
      return;
    }
    softPwmSetAll(r, g, b);
    bleRespond("OK:ALL:" + String(r) + "," + String(g) + "," + String(b));

  } else if (cmd == "SAVE") {
    solidSave();
    bleRespond("OK:SAVED");

  } else if (cmd == "STATUS") {
    bleRespond("STATUS:" + String(modeActive()) + ":" + String(modeName(modeActive())));

  } else if (cmd == "PING") {
    bleRespond("PONG");

  } else if (cmd == "GETSETTINGS") {
    String json;
    serializeJson(settings, json);
    bleRespond("SETTINGS:" + json);

  } else if (cmd.startsWith("SETSETTINGS:")) {
    JsonDocument doc;
    if (deserializeJson(doc, cmd.substring(12))) {
      bleRespond("ERR:INVALID_JSON");
      return;
    }
    settingsMerge(settings.as<JsonObject>(), doc.as<JsonObjectConst>());
    settingsSave();
    bleRespond("OK:SETTINGS_SAVED");

  } else if (cmd.startsWith("GET:MODESETTINGS:")) {
    int m = cmd.substring(17).toInt();
    if (m < 0 || m >= AURORA_MODE_COUNT) { bleRespond("ERR:INVALID_MODE"); return; }
    String json;
    serializeJson(settingsMode(m), json);
    bleRespond("MODESETTINGS:" + String(m) + ":" + json);

  } else if (cmd.startsWith("SET:MODESETTINGS:")) {
    // SET:MODESETTINGS:<mode>:<json>
    int c2 = cmd.indexOf(':', 17);
    if (c2 <= 0) { bleRespond("ERR:INVALID_FORMAT"); return; }
    int m = cmd.substring(17, c2).toInt();
    if (m < 0 || m >= AURORA_MODE_COUNT) { bleRespond("ERR:INVALID_MODE"); return; }
    JsonDocument doc;
    if (deserializeJson(doc, cmd.substring(c2 + 1))) {
      bleRespond("ERR:INVALID_JSON");
      return;
    }
    settings["MODE_" + String(m)] = doc;
    settingsSaveMode(m);
    bleRespond("OK:MODESETTINGS_SAVED:" + String(m));

  } else {
    bleRespond("ERR:UNKNOWN_CMD");
  }
}

// --- GATT plumbing ---------------------------------------------------------

class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer*) override {
    connected = true;
    Serial.println("[ble] connected");
  }
  void onDisconnect(BLEServer*) override {
    connected = false;
    Serial.println("[ble] disconnected");
  }
};

class CommandCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* ch) override {
    String value = ch->getValue();
    if (value.isEmpty()) return;
    cmdBuffer += value;

    // process complete lines only (multiple commands may share one write)
    int nl;
    while ((nl = cmdBuffer.indexOf('\n')) >= 0) {
      String line = cmdBuffer.substring(0, nl);
      cmdBuffer = cmdBuffer.substring(nl + 1);
      line.trim();
      if (line.length()) handleCommand(line);
    }
    if (cmdBuffer.length() > AURORA_BLE_CMD_BUFFER_MAX) {
      cmdBuffer = "";   // garbage without newline — drop it
    }
  }
};

void bleRespond(const String& response) {
  if (!connected || !responseChar) return;
  responseChar->setValue(response.c_str());
  responseChar->notify();
  Serial.println("[ble] => " + response);
}

void bleBegin() {
  BLEDevice::init(AURORA_DEVICE_NAME);

  server = BLEDevice::createServer();
  server->setCallbacks(new ServerCallbacks());

  BLEService* svc = server->createService(AURORA_BLE_SERVICE_UUID);

  BLECharacteristic* cmdChar = svc->createCharacteristic(
      AURORA_BLE_COMMAND_CHAR_UUID, BLECharacteristic::PROPERTY_WRITE);
  cmdChar->setCallbacks(new CommandCallbacks());

  responseChar = svc->createCharacteristic(
      AURORA_BLE_RESPONSE_CHAR_UUID,
      BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY);
  responseChar->addDescriptor(new BLE2902());

  svc->start();

  BLEAdvertising* adv = BLEDevice::getAdvertising();
  adv->addServiceUUID(AURORA_BLE_SERVICE_UUID);
  adv->setScanResponse(true);
  adv->setMinPreferred(0x06);
  adv->setMinPreferred(0x12);
  BLEDevice::startAdvertising();

  Serial.println("[ble] advertising as '" AURORA_DEVICE_NAME "'");
}

void bleLoop() {
  if (!connected && wasConnected) {
    delay(250);                 // let the stack settle
    BLEDevice::startAdvertising();
    Serial.println("[ble] re-advertising");
  }
  wasConnected = connected;
}

bool bleConnected() {
  return connected;
}
