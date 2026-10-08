// Configuración del cuarto frío.
// Copia este archivo como config.h y ajusta los valores. config.h está en .gitignore
// para que los números de teléfono reales no se suban al repositorio.
#pragma once

// Nombre que aparece en los reportes. No uses comas: el reporte es un texto separado por comas.
#define UBICACION "Cuarto frio no. 1"

// Número que recibe los reportes y las alarmas, en formato internacional.
#define NUMERO_REPORTES "+520000000000"

// Números que pueden cambiar la configuración por SMS. Se comparan los últimos 10 dígitos,
// así que da igual si el SMS llega con o sin el prefijo +52.
const char *const NUMEROS_AUTORIZADOS[] = {
  "+520000000000",
};

// DHT11 solo mide de 0 a 50 °C (±2 °C). Para un cuarto frío se recomienda DHT22 (-40 a 80 °C, ±0.5 °C).
#define TIPO_SENSOR DHT11

// true: muestra por el monitor serie lo que llega del módulo GSM y permite enviarle comandos AT.
#define MODO_DEPURACION false
