#pragma once
#include "Arduino.h"
struct SimEeprom {
  uint8_t memoria[1024];
  unsigned escrituras = 0;
  SimEeprom() { memset(memoria, 0xFF, sizeof memoria); }
  uint8_t read(int dir) { return memoria[dir]; }
  void update(int dir, uint8_t v) { if (memoria[dir] != v) { memoria[dir] = v; escrituras++; } }
  template <class T> void get(int dir, T &t) { memcpy(&t, memoria + dir, sizeof(T)); }
  template <class T> void put(int dir, const T &t) { const uint8_t *p = (const uint8_t *)&t; for (size_t i = 0; i < sizeof(T); i++) update(dir + i, p[i]); }
  int length() { return 1024; }
};
extern SimEeprom EEPROM;
