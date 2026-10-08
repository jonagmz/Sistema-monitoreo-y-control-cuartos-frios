#pragma once
#define WDTO_8S 9
extern int wdtResets, wdtEnabled;
inline void wdt_reset() { wdtResets++; }
inline void wdt_enable(int) { wdtEnabled = 1; }
