// Evalúa las alarmas (con retardo para temperatura y humedad) y decide cuándo avisar por SMS.
#pragma once
#include <Arduino.h>

void alarmasActualizar(uint32_t ahora);
// true si hay que enviar un aviso a los números de alertas (alarma nueva, resuelta o recordatorio).
bool alarmasHayQueAvisar(uint32_t ahora);
void alarmasAvisoEnviado(uint32_t ahora);
void alarmasReconocer();
