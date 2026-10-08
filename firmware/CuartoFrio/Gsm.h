// Módulo SIM800L: inicialización, recepción de SMS y cola de envíos. Nada bloquea el loop.
#pragma once
#include <Arduino.h>

const uint8_t DESTINO_REMITENTE = 0xFE;  // el número que mandó el último comando
// Cualquier otro destino es un índice de NUMEROS_ALERTAS.

struct Envio {
  char tipo;       // 'R', 'C', 'H', 'E' o 'X' (ver docs/PROTOCOLO.md)
  uint8_t destino;
  uint8_t param;   // H: página; E y X: código
  uint8_t param2;  // H: horas
};

void gsmIniciar();
void gsmAtender(uint32_t ahora);
bool gsmEncolar(const Envio &envio);
bool gsmRecibiendo();  // está llegando un mensaje: no conviene hacer nada que bloquee interrupciones
bool gsmListo();

// Los implementa Mensajes.cpp.
void mensajeRecibido(const char *numero, const char *texto);
size_t mensajeConstruir(const Envio &envio, char *destino, size_t tamano, uint32_t ahora);
const char *mensajeNumero(uint8_t destino);
