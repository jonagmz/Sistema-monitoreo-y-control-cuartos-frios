/*
  Control de cuarto frío con celdas Peltier — firmware v2.
  Arduino Uno + SIM800L (SMS) + DHT + NTC en el disipador + 4 relés o 2 MOSFET.
  Protocolo SMS en docs/PROTOCOLO.md; configuración en config.h (ver config.example.h).

  Módulos:
    Hal        pines y registros (relés, PWM a 25 kHz, ADC, causa del arranque)
    Ajustes    configuración en EEPROM con CRC y tabla de claves
    Sensores   DHT con mediana de 3 y NTC con promedio
    Control    PID, salida por etapas o PWM, protección del lado caliente, falla de enfriamiento
    Alarmas    retardos, avisos, recordatorios y reconocimiento
    Historial  buffer circular en EEPROM (48 h) y páginas para SMS
    Gsm        máquina de estados del SIM800L con cola de envíos
    Mensajes   comandos recibidos y mensajes enviados
    Pantalla   LCD 20x4
*/
#include <avr/wdt.h>
#include "Ajustes.h"
#include "Alarmas.h"
#include "Configuracion.h"
#include "Control.h"
#include "Estado.h"
#include "Gsm.h"
#include "Hal.h"
#include "Historial.h"
#include "Mensajes.h"
#include "Pantalla.h"
#include "Sensores.h"

Estado estado;

static uint32_t ultimoControl = 0, ultimoReporte = 0, ultimoMinuto = 0;

void setup() {
  hal::iniciarSalidas();  // lo primero: las celdas no deben encenderse al arrancar
  bool tras_corte_de_luz = hal::arranqueEnFrio();
  Serial.begin(115200);
  pantallaIniciar();

  uint32_t ahora = millis();
  bool eepromNueva = ajustesCargar();
  historialIniciar(eepromNueva, ahora);
  historialMarcarInicio(ahora);  // la app sabe que aquí hubo un hueco de duración desconocida
  sensoresIniciar();
  controlIniciar(ahora);
  gsmIniciar();
  avisarAlertas('E', tras_corte_de_luz ? EVENTO_INICIO_LUZ : EVENTO_INICIO_REINICIO);

  ultimoControl = ultimoReporte = ultimoMinuto = ahora;
  wdt_enable(WDTO_8S);  // si algo se cuelga, el Arduino se reinicia solo
}

void loop() {
  wdt_reset();
  uint32_t ahora = millis();

  gsmAtender(ahora);
  sensoresAtender(ahora, !gsmRecibiendo());

  if (ahora - ultimoControl >= PERIODO_CONTROL) {
    ultimoControl = ahora;
    controlActualizar(ahora);
    alarmasActualizar(ahora);
    historialAcumular(ahora);
    if (alarmasHayQueAvisar(ahora)) {
      avisarAlertas('R', 0);
      alarmasAvisoEnviado(ahora);
      ultimoReporte = ahora;  // el aviso ya sirve de reporte
    }
  }

  if (ajustes.ri && ahora - ultimoReporte >= (uint32_t)ajustes.ri * 60000UL) {
    ultimoReporte = ahora;
    avisarAlertas('R', 0);
  }

  if (ahora - ultimoMinuto >= 60000UL) {
    ultimoMinuto += 60000UL;
    estado.minutosEncendido++;
  }

  pantallaActualizar(ahora);
}
