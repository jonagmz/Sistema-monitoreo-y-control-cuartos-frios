#pragma once
#include "Arduino.h"
struct LiquidCrystal_I2C {
  std::string filas[4];
  int fila = 0;
  static LiquidCrystal_I2C *instancia;
  LiquidCrystal_I2C(int, int, int) { instancia = this; }
  void init() {}
  void backlight() {}
  void clear() { for (auto &f : filas) f.clear(); }
  void setCursor(int, int f) { fila = f; }
  void print(const char *s) { filas[fila] = s; }
};
