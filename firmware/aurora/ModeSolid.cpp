// ============================================================================
// Aurora — ModeSolid.cpp  (mode 1)
// Static 4-corner colors. State lives in Solid.cpp so BLE/web can edit the
// corners live even before this mode is restarted.
// ============================================================================
#include "Modes.h"
#include "Solid.h"

void solidBegin() {
  solidLoad();     // settings → corner colors
  solidApply();    // corner colors → LEDs
}

void solidTick(uint32_t) {
  // static image — nothing to do
}

void solidEnd() {
  solidOff();
}
