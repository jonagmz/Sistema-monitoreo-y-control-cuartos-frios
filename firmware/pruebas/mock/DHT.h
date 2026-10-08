#pragma once
#include "Arduino.h"
#define DHT11 11
#define DHT22 22
extern float simTemp, simHum;
extern int simLecturasDht;
struct DHT {
  DHT(int, int) {}
  void begin() {}
  float readTemperature() { simLecturasDht++; return simTemp; }
  float readHumidity() { return std::isnan(simTemp) ? NAN : simHum; }
};
