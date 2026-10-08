#include "Historial.h"
#include "Ajustes.h"
#include "Estado.h"
#include <EEPROM.h>

// Registro: [secuencia, temperatura (medios °C, con signo), humedad %, lado caliente °C, potencia/alarmas].
// La secuencia (0..254, 255 = vacío) permite encontrar el registro más reciente sin guardar un puntero
// que se reescribiría en cada muestra y gastaría esa celda de la EEPROM.
static const uint16_t INICIO = 64;  // los ajustes ocupan los primeros bytes
static const uint8_t TAMANO = 5;
static const uint16_t CAPACIDAD = (1024 - INICIO) / TAMANO;  // 192 muestras = 48 h cada 15 min
static const uint8_t VACIO = 0xFF;
static const int8_t T_SIN_DATO = -128;
static const uint8_t H_MARCA_INICIO = 254;

static int16_t cabeza = -1;  // índice del registro más reciente
static uint16_t cantidad = 0;
static uint32_t ultimaMuestra = 0;

static int32_t sumaTemp = 0, sumaHum = 0, sumaCal = 0, sumaPot = 0;
static uint16_t nTemp = 0, nHum = 0, nCal = 0, nPot = 0;
static uint8_t alarmasIntervalo = 0;

static uint8_t secuencia(uint16_t i) {
  return EEPROM.read(INICIO + i * TAMANO);
}

static void escribir(const uint8_t datos[4]) {
  uint8_t sec = cabeza < 0 ? 0 : (secuencia(cabeza) + 1) % 255;
  cabeza = (cabeza + 1) % CAPACIDAD;
  uint16_t dir = INICIO + cabeza * TAMANO;
  EEPROM.update(dir, sec);
  for (uint8_t i = 0; i < 4; i++) EEPROM.update(dir + 1 + i, datos[i]);
  if (cantidad < CAPACIDAD) cantidad++;
}

void historialIniciar(bool borrar, uint32_t ahora) {
  ultimaMuestra = ahora;
  if (borrar) {
    for (uint16_t i = 0; i < CAPACIDAD; i++) EEPROM.update(INICIO + i * TAMANO, VACIO);
    cabeza = -1;
    cantidad = 0;
    return;
  }
  // El más reciente es el único registro cuyo siguiente está vacío o no tiene la secuencia siguiente.
  cabeza = -1;
  for (uint16_t i = 0; i < CAPACIDAD; i++) {
    uint8_t s = secuencia(i);
    if (s == VACIO) continue;
    uint8_t siguiente = secuencia((i + 1) % CAPACIDAD);
    if (siguiente == VACIO || siguiente != (s + 1) % 255) {
      cabeza = i;
      break;
    }
  }
  if (cabeza < 0) {
    cantidad = 0;
  } else {
    cantidad = secuencia((cabeza + 1) % CAPACIDAD) == VACIO ? cabeza + 1 : CAPACIDAD;
  }
}

void historialMarcarInicio(uint32_t ahora) {
  const uint8_t marca[4] = {(uint8_t)T_SIN_DATO, H_MARCA_INICIO, 255, 0};
  escribir(marca);
  ultimaMuestra = ahora;
}

static uint8_t bitsAlarma(uint8_t alarmas) {
  uint8_t b = 0;
  if (alarmas & (AL_TEMP_ALTA | AL_TEMP_BAJA | AL_HUM_ALTA | AL_HUM_BAJA)) b |= 0x10;
  if (alarmas & (AL_SENSOR | AL_SENSOR_CALIENTE)) b |= 0x20;
  if (alarmas & AL_SOBRECALENTADO) b |= 0x40;
  if (alarmas & AL_FALLA_ENFRIAMIENTO) b |= 0x80;
  return b;
}

void historialAcumular(uint32_t ahora) {
  if (estado.temp != SIN_DATO) { sumaTemp += estado.temp; nTemp++; }
  if (estado.hum != SIN_DATO) { sumaHum += estado.hum; nHum++; }
  if (estado.caliente != SIN_DATO) { sumaCal += estado.caliente; nCal++; }
  sumaPot += estado.potencia;
  nPot++;
  alarmasIntervalo |= bitsAlarma(estado.alarmas);

  if (ahora - ultimaMuestra < (uint32_t)ajustes.hi * 60000UL) return;
  ultimaMuestra = ahora;

  uint8_t datos[4];
  // Promedios: medio grado para la temperatura (-64 a 63.5 °C), % y °C enteros para el resto.
  datos[0] = nTemp ? (uint8_t)(int8_t)constrain((sumaTemp / nTemp + (sumaTemp >= 0 ? 2 : -2)) / 5, -127, 127) : (uint8_t)T_SIN_DATO;
  datos[1] = nHum ? (uint8_t)constrain((sumaHum / nHum + 5) / 10, 0, 100) : 255;
  datos[2] = nCal ? (uint8_t)constrain((sumaCal / nCal + 5) / 10, 0, 254) : 255;
  datos[3] = (uint8_t)((sumaPot / nPot * 15 + 50) / 100) | alarmasIntervalo;
  escribir(datos);

  sumaTemp = sumaHum = sumaCal = sumaPot = 0;
  nTemp = nHum = nCal = nPot = 0;
  alarmasIntervalo = 0;
}

uint16_t historialCantidad() { return cantidad; }
uint16_t historialCapacidad() { return CAPACIDAD; }

static uint16_t muestrasPedidas(uint8_t horas) {
  uint32_t porHoras = (uint32_t)horas * 60 / ajustes.hi;
  return porHoras < cantidad ? (uint16_t)porHoras : cantidad;
}

uint8_t historialPaginas(uint8_t horas) {
  return (muestrasPedidas(horas) + MUESTRAS_POR_PAGINA - 1) / MUESTRAS_POR_PAGINA;
}

static const char BASE64URL[] PROGMEM = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

size_t historialPagina(char *destino, size_t tamano, uint8_t pagina, uint8_t horas, uint32_t ahora) {
  uint16_t m = muestrasPedidas(horas);
  uint8_t total = historialPaginas(horas);
  uint16_t desdeK = (uint16_t)(pagina - 1) * MUESTRAS_POR_PAGINA;
  uint16_t hastaK = (desdeK + MUESTRAS_POR_PAGINA < m) ? desdeK + MUESTRAS_POR_PAGINA : m;
  int n = snprintf(destino, tamano, "CF2;H;%u/%u;I%u;E%lu;N%u;", pagina, total, ajustes.hi,
                   (unsigned long)((ahora - ultimaMuestra) / 60000UL), m - hastaK);

  uint8_t bytes[MUESTRAS_POR_PAGINA * 4];
  uint8_t largo = 0;
  for (uint16_t k = desdeK; k < hastaK; k++) {
    uint16_t indice = (cabeza + CAPACIDAD - (m - 1 - k)) % CAPACIDAD;  // k = 0 es la más antigua
    for (uint8_t i = 0; i < 4; i++) bytes[largo++] = EEPROM.read(INICIO + indice * TAMANO + 1 + i);
  }
  // base64url sin relleno
  for (uint8_t i = 0; i < largo && (size_t)n + 4 < tamano; i += 3) {
    uint32_t grupo = (uint32_t)bytes[i] << 16 | (i + 1 < largo ? (uint32_t)bytes[i + 1] << 8 : 0) | (i + 2 < largo ? bytes[i + 2] : 0);
    uint8_t caracteres = i + 2 < largo ? 4 : (i + 1 < largo ? 3 : 2);
    for (uint8_t c = 0; c < caracteres; c++) destino[n++] = pgm_read_byte(&BASE64URL[(grupo >> (18 - 6 * c)) & 0x3F]);
  }
  destino[n] = '\0';
  return n;
}
