// PID de temperatura, protección del lado caliente y salida a las celdas Peltier.
#pragma once
#include <Arduino.h>

const uint32_t PERIODO_CONTROL = 5000;

void controlIniciar(uint32_t ahora);
// Se llama cada PERIODO_CONTROL. Actualiza estado.potencia, protegiendo y fallaEnfriamiento.
void controlActualizar(uint32_t ahora);

// Límite de potencia (%) por la temperatura del lado caliente. Actualiza estado.protegiendo y bloqueoTermico.
uint8_t limitePorLadoCaliente(int16_t caliente, bool fallaSensor, uint8_t maximoC, uint32_t ahora);
// Quita el bloqueo térmico (lo llama el comando ON).
void controlDesbloquear();
