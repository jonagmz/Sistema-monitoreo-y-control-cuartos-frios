#include "Hal.h"
#include "Configuracion.h"

namespace hal {

void iniciarSalidas() {
#if MODO_SALIDA == SALIDA_RELES
  for (uint8_t pin : PINES_RELE) {
    digitalWrite(pin, RELE_ACTIVO_EN_BAJO ? HIGH : LOW);  // antes de pinMode: el relé no parpadea al arrancar
    pinMode(pin, OUTPUT);
  }
#else
  digitalWrite(PIN_PWM_A, LOW);
  digitalWrite(PIN_PWM_B, LOW);
  pinMode(PIN_PWM_A, OUTPUT);
  pinMode(PIN_PWM_B, OUTPUT);
#if defined(__AVR__)
  // Timer1 en PWM de fase correcta con TOP = ICR1: 16 MHz / (2 · 320) = 25 kHz, inaudible.
  TCCR1A = _BV(COM1A1) | _BV(COM1B1) | _BV(WGM11);
  TCCR1B = _BV(WGM13) | _BV(CS10);
  ICR1 = 320;
  OCR1A = 0;
  OCR1B = 0;
#endif
#endif
#if PIN_GSM_RESET >= 0
  digitalWrite(PIN_GSM_RESET, HIGH);
  pinMode(PIN_GSM_RESET, OUTPUT);
#endif
}

void rele(uint8_t celda, bool encendida) {
  digitalWrite(PINES_RELE[celda], encendida == RELE_ACTIVO_EN_BAJO ? LOW : HIGH);
}

void pwm(uint8_t porcentaje) {
#if defined(__AVR__)
  OCR1A = OCR1B = (uint16_t)((uint32_t)porcentaje * 320 / 100);
#else
  analogWrite(PIN_PWM_A, porcentaje);
#endif
}

uint16_t leerNtc() {
  uint16_t suma = 0;
  for (uint8_t i = 0; i < 16; i++) suma += analogRead(PIN_NTC);
  return suma / 16;
}

#if defined(__AVR__)
// .noinit no se borra al reiniciar: si la marca sigue ahí, la RAM no perdió energía.
static uint32_t marcaRam __attribute__((section(".noinit")));
#else
uint32_t marcaRam;
#endif

bool arranqueEnFrio() {
  const uint32_t MARCA = 0xC0FFEE42;
  bool frio = marcaRam != MARCA;
  marcaRam = MARCA;
  return frio;
}

void reiniciarModem() {
#if PIN_GSM_RESET >= 0
  digitalWrite(PIN_GSM_RESET, LOW);
  delay(150);
  digitalWrite(PIN_GSM_RESET, HIGH);
#endif
}

}  // namespace hal
