// Estado compartido entre módulos. Temperaturas y humedad en décimas (45 = 4.5 °C).
#pragma once
#include <Arduino.h>

const int16_t SIN_DATO = INT16_MIN;

// Máscara de alarmas (igual que el campo A del protocolo).
const uint8_t AL_TEMP_ALTA = 0x01;
const uint8_t AL_TEMP_BAJA = 0x02;
const uint8_t AL_HUM_ALTA = 0x04;
const uint8_t AL_HUM_BAJA = 0x08;
const uint8_t AL_SENSOR = 0x10;
const uint8_t AL_SOBRECALENTADO = 0x20;
const uint8_t AL_SENSOR_CALIENTE = 0x40;
const uint8_t AL_FALLA_ENFRIAMIENTO = 0x80;

struct Estado {
  int16_t temp = SIN_DATO;      // ambiente
  int16_t hum = SIN_DATO;
  int16_t caliente = SIN_DATO;  // disipador del lado caliente
  bool fallaAmbiente = false;
  bool fallaCaliente = false;
  bool protegiendo = false;     // celdas apagadas por sobrecalentamiento
  bool bloqueoTermico = false;  // 3 disparos en 1 h: apagadas hasta recibir ON
  uint32_t ultimoDisparo = 0;   // millis() del último sobrecalentamiento
  bool fallaEnfriamiento = false;
  uint8_t potencia = 0;         // % aplicado a las celdas
  uint8_t alarmas = 0;
  uint8_t senal = 99;           // CSQ del módulo GSM
  uint32_t minutosEncendido = 0;
};

extern Estado estado;
