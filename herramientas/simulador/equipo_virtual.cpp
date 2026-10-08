// Equipo virtual: el firmware real con un modelo térmico de cuarto frío con celdas Peltier.
// Órdenes por stdin (una por línea):
//   SMS <numero> <texto>    llega un SMS al módulo GSM
//   AMBIENTE <°C>           temperatura exterior
//   PUERTA <segundos>       puerta abierta: entra calor mucho más rápido
//   VENTILADOR <0|1>        0 = falla el ventilador del disipador
//   SENSOR <0|1>            0 = se desconecta el sensor de ambiente
//   RUN <ms>                avanza el tiempo simulado
// Salidas: "SMS_OUT\t<numero>\t<texto>" y "ESTADO\t<seg>\t<temp>\t<hum>\t<caliente>\t<potencia>\t<lcd3>"
#include "Arduino.h"
#include "EEPROM.h"
#include "SoftwareSerial.h"
#include "DHT.h"
#include "LiquidCrystal_I2C.h"
#include <iostream>
#include <sstream>

unsigned long simMillis = 0;
std::map<int, int> simPines, simModos, simAnalogico;
SimSerie Serial;
SimEeprom EEPROM;
float simTemp = 12.0f, simHum = 82.0f;
int simLecturasDht = 0, simWatchdog = 0;
SoftwareSerial *SoftwareSerial::instancia = nullptr;
LiquidCrystal_I2C *LiquidCrystal_I2C::instancia = nullptr;

#include "../../firmware/CuartoFrio/CuartoFrio.ino"

static float temperatura = 12.0f, caliente = 27.0f, ambiente = 25.0f;
static long puertaHasta = 0;
static bool ventilador = true, sensorConectado = true;

static int adcNtc(float c) {
  double r = 10000.0 * exp(3950.0 * (1.0 / (c + 273.15) - 1.0 / 298.15));
  return (int)lround(1023.0 * r / (10000.0 + r));
}

// Un paso de 1 s del modelo térmico.
static void fisica() {
  float p = estado.potencia / 100.0f;
  float fuga = (simMillis / 1000 < (unsigned long)puertaHasta) ? 0.004f : 0.0004f;   // 1/s
  float enfriamiento = 0.010f * p * (caliente < 70 ? 1.0f : 0.5f);                   // °C/s; con el disipador caliente rinde menos
  temperatura += fuga * (ambiente - temperatura) - enfriamiento;
  float objetivoCaliente = ambiente + (ventilador ? 32.0f : 75.0f) * p;
  caliente += (objetivoCaliente - caliente) / 60.0f;
  simTemp = sensorConectado ? temperatura : NAN;
  simHum = 88.0f - 10.0f * p + (puertaHasta > (long)(simMillis / 1000) ? 6.0f : 0.0f);
  simAnalogico[A0] = adcNtc(caliente);
}

static size_t smsVistos = 0;
static void emitirSms() {
  auto &enviados = SoftwareSerial::instancia->enviados;
  for (; smsVistos < enviados.size(); smsVistos++)
    std::cout << "SMS_OUT\t" << enviados[smsVistos].numero << "\t" << enviados[smsVistos].texto << std::endl;
}

int main() {
  simAnalogico[A0] = adcNtc(caliente);
  setup();
  std::string linea;
  while (std::getline(std::cin, linea)) {
    std::istringstream in(linea);
    std::string orden;
    in >> orden;
    if (orden == "SMS") {
      std::string numero, texto;
      in >> numero;
      std::getline(in, texto);
      SoftwareSerial::instancia->recibirSms(numero, texto.substr(texto.find_first_not_of(' ')));
    } else if (orden == "AMBIENTE") {
      in >> ambiente;
    } else if (orden == "PUERTA") {
      long s; in >> s; puertaHasta = simMillis / 1000 + s;
    } else if (orden == "VENTILADOR") {
      int v; in >> v; ventilador = v;
    } else if (orden == "SENSOR") {
      int v; in >> v; sensorConectado = v;
    } else if (orden == "RUN") {
      unsigned long ms; in >> ms;
      unsigned long fin = simMillis + ms, segundo = simMillis / 1000;
      while (simMillis < fin) {
        loop();
        simMillis += 50;
        if (simMillis / 1000 != segundo) { segundo = simMillis / 1000; fisica(); }
        emitirSms();
      }
      std::cout << "ESTADO\t" << simMillis / 1000 << "\t" << temperatura << "\t" << simHum << "\t" << caliente << "\t"
                << (int)estado.potencia << "\t" << LiquidCrystal_I2C::instancia->filas[3] << std::endl;
    }
  }
}
