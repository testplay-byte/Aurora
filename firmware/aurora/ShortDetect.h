// ============================================================================
// Aurora — ShortDetect.h
// Short-circuit protection: sense pin → debounce → alert blinks → log →
// restore previous mode.
// ============================================================================
#ifndef AURORA_SHORTDETECT_H
#define AURORA_SHORTDETECT_H

#include <Arduino.h>

void shortDetectBegin();          // pin setup + grace period start
void shortDetectUpdate();         // call every loop (cheap)
bool shortAlertActive();          // true while alert blinks run (gates mode tick)
bool shortDetected();             // true while the sense line is LOW

#endif  // AURORA_SHORTDETECT_H
