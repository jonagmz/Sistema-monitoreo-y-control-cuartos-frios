// Configuración del equipo. Copia este archivo como config.h (está en .gitignore) y ajústalo.
#pragma once

// Números que pueden enviar comandos (se comparan los últimos 10 dígitos), separados por comas.
#define NUMEROS_AUTORIZADOS "+520000000000"
// Números que reciben alarmas, avisos de corte de luz y el reporte periódico.
#define NUMEROS_ALERTAS "+520000000000"
// PIN que deben incluir los comandos. "" = sin PIN (no recomendado: los SMS se pueden falsificar).
#define PIN_SEGURIDAD "1234"

// Sensor de ambiente: DHT11 (0 a 50 °C, ±2 °C) o DHT22 (-40 a 80 °C, ±0.5 °C, recomendado).
#define TIPO_SENSOR DHT11

// Salida a las celdas Peltier:
//   SALIDA_RELES: 4 relés en los pines 3 a 6; el PID enciende de 0 a 4 celdas (control por etapas).
//   SALIDA_PWM:   MOSFET en los pines 9 y 10 con PWM a 25 kHz; el PID regula la potencia de 0 a 100 %.
#define MODO_SALIDA SALIDA_RELES
// true si los módulos de relé se activan con LOW (lo más común).
#define RELE_ACTIVO_EN_BAJO true

// Termistor NTC de 10 kΩ en el disipador del lado caliente (A0, con resistencia de 10 kΩ a 5 V).
#define USAR_SENSOR_CALIENTE true
#define NTC_BETA 3950

// Pin conectado al RST del SIM800L para reiniciarlo si deja de responder (-1 = no conectado).
#define PIN_GSM_RESET -1

// Nombre que se muestra en la pantalla al encender.
#define NOMBRE_EQUIPO "Cuarto frio 1"

// true: muestra por el monitor serie el tráfico con el módulo GSM.
#define MODO_DEPURACION false
