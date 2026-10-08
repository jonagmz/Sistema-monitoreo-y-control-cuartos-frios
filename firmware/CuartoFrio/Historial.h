// Historial en la EEPROM: una muestra promediada cada ajustes.hi minutos, sobrevive a cortes de luz.
#pragma once
#include <Arduino.h>

const uint8_t MUESTRAS_POR_PAGINA = 24;  // 96 bytes = 128 caracteres base64: cabe en un SMS con el encabezado

void historialIniciar(bool borrar, uint32_t ahora);  // borrar = EEPROM sin datos válidos
void historialMarcarInicio(uint32_t ahora);           // marca el reinicio (corte de luz) en el historial
void historialAcumular(uint32_t ahora);                // en cada ciclo de control
uint16_t historialCantidad();
uint16_t historialCapacidad();
uint8_t historialPaginas(uint8_t horas);
// Escribe la página (1..total) del historial de las últimas "horas" en formato CF2;H. Devuelve la longitud.
size_t historialPagina(char *destino, size_t tamano, uint8_t pagina, uint8_t horas, uint32_t ahora);
