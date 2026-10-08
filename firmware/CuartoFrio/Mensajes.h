// Comandos que llegan por SMS y textos que envía el equipo (docs/PROTOCOLO.md).
#pragma once
#include <Arduino.h>

const uint8_t EVENTO_INICIO_LUZ = 0;        // volvió la energía tras un corte
const uint8_t EVENTO_INICIO_REINICIO = 1;   // watchdog o botón de reset

void avisarAlertas(char tipo, uint8_t param);  // encola un mensaje para cada número de alertas
