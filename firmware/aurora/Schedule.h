// ============================================================================
// Aurora — Schedule.h
// Auto turn-on/off + NTP time sync (needs a network — see docs/FLASHING.md).
// ============================================================================
#ifndef AURORA_SCHEDULE_H
#define AURORA_SCHEDULE_H

#include <Arduino.h>

// Reconfigure NTP (call after Wi-Fi is up, then hourly to keep time fresh).
void scheduleSyncTime();

// Check the auto on/off windows (call once a minute from loop()).
// No-ops until NTP has produced a valid time.
void scheduleCheck();

#endif  // AURORA_SCHEDULE_H
