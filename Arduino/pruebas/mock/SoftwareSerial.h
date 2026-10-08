#pragma once
#include "Arduino.h"
struct SoftwareSerial : Printer {
  size_t acked = 0;
  SoftwareSerial(int, int) {}
  int available() {  // simula el prompt '>' del SIM800L tras AT+CMGS
    size_t n = 0, pos = 0; while ((pos = out.find("AT+CMGS=", pos)) != std::string::npos) { n++; pos++; }
    while (acked < n) { in.push_back('>'); acked++; }
    return (int)in.size();
  }
  void feed(const std::string &s) { for (char c : s) in.push_back(c); }
};
