#pragma once
#include "Arduino.h"
#define DHT11 11
#define DHT22 22
extern float simTemp, simHum;
struct DHT { DHT(int, int) {} void begin() {} float readTemperature() { return simTemp; } float readHumidity() { return simHum; } };
