#pragma once
#include "Arduino.h"
struct LiquidCrystal_I2C { std::string rows[4]; int r = 0;
  LiquidCrystal_I2C(int, int, int) {} void init() {} void backlight() {} void clear() { for (auto &x : rows) x = ""; }
  void setCursor(int, int row) { r = row; } void print(const char *s) { rows[r] = s; } };
