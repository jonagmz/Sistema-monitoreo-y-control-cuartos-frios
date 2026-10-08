// Sensor de ambiente (DHT) y termistor del lado caliente. Actualizan estado.temp, hum y caliente.
#pragma once
#include <Arduino.h>

void sensoresIniciar();
// Lee lo que toque según el tiempo. puedeBloquear = false mientras llega un SMS:
// el DHT desactiva interrupciones unos milisegundos y SoftwareSerial perdería caracteres.
void sensoresAtender(uint32_t ahora, bool puedeBloquear);

// Temperatura en décimas de °C a partir de la lectura del ADC de un NTC con resistencia fija de 10 kΩ.
int16_t ntcADecimas(uint16_t adc, uint16_t beta);
