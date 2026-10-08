// Ajustes que se cambian por SMS y se guardan en la EEPROM.
#pragma once
#include <Arduino.h>

struct Ajustes {
  uint8_t version;
  int16_t sp, tl, th;     // temperatura objetivo y alarmas, décimas de °C
  uint8_t hl, hh;         // alarmas de humedad, %
  uint8_t ad;             // retardo de alarmas, min
  uint16_t ri;            // reporte periódico, min (0 = no)
  uint8_t ra;             // recordatorio de alarmas, h (0 = no)
  uint8_t hi;             // intervalo del historial, min
  uint8_t pm;             // potencia máxima, %
  uint8_t pc;             // máximo del lado caliente, °C
  uint16_t kp, ki, kd;    // PID, décimas
  uint8_t en;             // control encendido
  uint8_t crc;
};

extern Ajustes ajustes;

// Devuelve true si la EEPROM no tenía ajustes válidos y se guardaron los de fábrica.
bool ajustesCargar();
void ajustesGuardar();

// Aplica un "CLAVE=valor" sobre a. Devuelve false si la clave no existe o el valor está fuera de rango.
bool ajustesAplicar(Ajustes &a, const char *clave, const char *valor);
bool ajustesCoherentes(const Ajustes &a);
// Escribe "SP4.0;TL1.0;..." con todos los ajustes. Devuelve los caracteres escritos.
size_t ajustesEscribir(const Ajustes &a, char *destino, size_t tamano);

// Escribe décimas como texto ("-4.5"). Devuelve los caracteres escritos.
size_t escribirDecimas(char *destino, size_t tamano, int16_t decimas);
