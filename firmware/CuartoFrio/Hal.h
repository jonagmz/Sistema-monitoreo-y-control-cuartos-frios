// Capa de hardware: el único módulo que toca pines y registros.
#pragma once
#include <Arduino.h>

namespace hal {
const uint8_t PIN_DHT = 2;
const uint8_t PIN_GSM_RX = 7;
const uint8_t PIN_GSM_TX = 8;
const uint8_t PIN_NTC = A0;
const uint8_t PINES_RELE[4] = {3, 4, 5, 6};
const uint8_t PIN_PWM_A = 9;   // OC1A
const uint8_t PIN_PWM_B = 10;  // OC1B

void iniciarSalidas();
void rele(uint8_t celda, bool encendida);
void pwm(uint8_t porcentaje);  // los dos canales a la vez
uint16_t leerNtc();            // promedio de 16 lecturas del ADC (0 a 1023)
bool arranqueEnFrio();         // true tras un corte de luz; false tras watchdog o botón de reset
void reiniciarModem();
}  // namespace hal
