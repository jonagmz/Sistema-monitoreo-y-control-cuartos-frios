#pragma once
#define WDTO_8S 9
extern int simWatchdog;
inline void wdt_enable(int) { simWatchdog = 1; }
inline void wdt_reset() {}
