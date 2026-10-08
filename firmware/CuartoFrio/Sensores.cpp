#include "Sensores.h"
#include "Configuracion.h"
#include "Estado.h"
#include "Hal.h"
#include "DHT.h"
#include <math.h>

static DHT dht(hal::PIN_DHT, TIPO_SENSOR);

static const uint32_t PERIODO_DHT = 2500;  // el DHT11/22 no da lecturas nuevas más rápido
static const uint32_t PERIODO_NTC = 1000;
static const uint8_t FALLAS_PARA_ALARMA = 5;

static int16_t ultimasTemp[3], ultimasHum[3];
static uint8_t lecturasValidas = 0, posicion = 0, fallasSeguidas = 0;  // lecturasValidas llega como mucho a 3
static uint32_t ultimoDht = 0, ultimoNtc = 0;

static int16_t mediana3(const int16_t v[3]) {
  int16_t a = v[0], b = v[1], c = v[2];
  if (a > b) { int16_t t = a; a = b; b = t; }
  if (b > c) b = c;
  return a > b ? a : b;
}

int16_t ntcADecimas(uint16_t adc, uint16_t beta) {
  if (adc < 15 || adc > 1008) return SIN_DATO;  // cortocircuito o cable abierto
  float resistencia = 10000.0f * adc / (1023.0f - adc);
  float kelvin = 1.0f / (1.0f / 298.15f + logf(resistencia / 10000.0f) / beta);
  return (int16_t)lroundf((kelvin - 273.15f) * 10.0f);
}

void sensoresIniciar() {
  dht.begin();
}

static void leerDht() {
  float t = dht.readTemperature();
  float h = dht.readHumidity();
  if (isnan(t) || isnan(h)) {
    if (fallasSeguidas < 255) fallasSeguidas++;
    if (fallasSeguidas >= FALLAS_PARA_ALARMA) {
      estado.fallaAmbiente = true;
      estado.temp = estado.hum = SIN_DATO;
      lecturasValidas = 0;
    }
    return;
  }
  fallasSeguidas = 0;
  estado.fallaAmbiente = false;
  // Mediana de las 3 últimas lecturas: elimina picos sueltos, frecuentes en el DHT.
  ultimasTemp[posicion] = (int16_t)lroundf(t * 10);
  ultimasHum[posicion] = (int16_t)lroundf(h * 10);
  if (lecturasValidas < 3) lecturasValidas++;
  if (lecturasValidas < 3) {
    estado.temp = ultimasTemp[posicion];
    estado.hum = ultimasHum[posicion];
  } else {
    estado.temp = mediana3(ultimasTemp);
    estado.hum = mediana3(ultimasHum);
  }
  posicion = (posicion + 1) % 3;
}

void sensoresAtender(uint32_t ahora, bool puedeBloquear) {
  if (puedeBloquear && ahora - ultimoDht >= PERIODO_DHT) {
    ultimoDht = ahora;
    leerDht();
  }
#if USAR_SENSOR_CALIENTE
  if (ahora - ultimoNtc >= PERIODO_NTC) {
    ultimoNtc = ahora;
    int16_t c = ntcADecimas(hal::leerNtc(), NTC_BETA);
    estado.fallaCaliente = c == SIN_DATO;
    // Promedio móvil exponencial (1/4) para suavizar el ruido del ADC, con redondeo para no quedarse corto.
    if (c == SIN_DATO || estado.caliente == SIN_DATO) {
      estado.caliente = c;
    } else {
      int16_t diferencia = c - estado.caliente;
      estado.caliente += (diferencia + (diferencia > 0 ? 2 : -2)) / 4;
    }
  }
#endif
}
