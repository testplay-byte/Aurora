// ============================================================================
// Aurora — Ble.h
// BLE GATT service: write characteristic in, notify characteristic out.
// Protocol reference: docs/BLE-PROTOCOL.md
// ============================================================================
#ifndef AURORA_BLE_H
#define AURORA_BLE_H

#include <Arduino.h>

void bleBegin();                 // create service + start advertising
void bleLoop();                  // re-advertise after disconnects (call each loop)
bool bleConnected();
void bleRespond(const String& response);   // notify (no-op when disconnected)

#endif  // AURORA_BLE_H
