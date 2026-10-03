// ============================================================================
// Aurora — ModeOff.cpp  (mode 0)
// ============================================================================
#include "Modes.h"
#include "SoftPwm.h"

void offBegin() {
  softPwmOff();
}

void offTick(uint32_t) {
  // nothing to animate
}

void offEnd() {
  // nothing to stop
}
