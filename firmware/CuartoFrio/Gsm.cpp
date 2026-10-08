#include "Gsm.h"
#include "Configuracion.h"
#include "Estado.h"
#include "Hal.h"
#include <SoftwareSerial.h>

static SoftwareSerial modem(hal::PIN_GSM_RX, hal::PIN_GSM_TX);

enum Fase : uint8_t { INICIANDO, ESPERA_OK, LISTO, ESPERA_PROMPT, ESPERA_ENVIO, PAUSA };

static const char AT_0[] PROGMEM = "AT";
static const char AT_1[] PROGMEM = "ATE0";
static const char AT_2[] PROGMEM = "AT+CMGF=1";               // SMS en modo texto
static const char AT_3[] PROGMEM = "AT+CSCS=\"GSM\"";
static const char AT_4[] PROGMEM = "AT+CNMI=2,2,0,0,0";       // los SMS llegan por el puerto serie, sin guardarse
static const char AT_5[] PROGMEM = "AT+CMGDA=\"DEL ALL\"";    // vacía la SIM para que nunca se llene
static const char *const SECUENCIA_INICIO[] PROGMEM = {AT_0, AT_1, AT_2, AT_3, AT_4, AT_5};
static const uint8_t PASOS_INICIO = sizeof(SECUENCIA_INICIO) / sizeof(SECUENCIA_INICIO[0]);

static const uint32_t ESPERA_RESPUESTA = 3000;
static const uint32_t ESPERA_PROMPT_MAX = 10000;
static const uint32_t ESPERA_ENVIO_MAX = 60000;
static const uint32_t PERIODO_SENAL = 5UL * 60000UL;
static const uint8_t INTENTOS_ENVIO = 3;
static const uint8_t FALLOS_PARA_REINICIAR = 5;

static Fase fase = INICIANDO, faseTrasPausa = INICIANDO;
static uint32_t desdeFase = 0, duracionPausa = 0, ultimaSenal = 0;
static uint8_t pasoInicio = 0, fallosSeguidos = 0, intentos = 0;
static bool consultandoSenal = false, envioConfirmado = false, senalPendiente = true;

static char linea[100];
static uint8_t largo = 0;
static bool esperandoCuerpo = false;
static char remitente[20];

static const uint8_t TAMANO_COLA = 12;  // suficiente para un historial completo (8 páginas) y avisos
static Envio cola[TAMANO_COLA];
static uint8_t colaInicio = 0, colaCantidad = 0;
static char salida[161];

static void cambiarFase(Fase nueva, uint32_t ahora) {
  fase = nueva;
  desdeFase = ahora;
}

static void pausar(uint32_t ms, Fase despues, uint32_t ahora) {
  duracionPausa = ms;
  faseTrasPausa = despues;
  cambiarFase(PAUSA, ahora);
}

static void fallo(uint32_t ahora) {
  if (++fallosSeguidos >= FALLOS_PARA_REINICIAR) {
    fallosSeguidos = 0;
    hal::reiniciarModem();
    pasoInicio = 0;
    pausar(10000, INICIANDO, ahora);  // el SIM800L tarda en registrarse en la red
  } else {
    pausar(2000, pasoInicio < PASOS_INICIO ? INICIANDO : LISTO, ahora);
  }
}

static void terminarEnvio(bool exito, uint32_t ahora) {
  if (exito || ++intentos >= INTENTOS_ENVIO) {
    colaInicio = (colaInicio + 1) % TAMANO_COLA;
    colaCantidad--;
    intentos = 0;
  }
  if (exito) {
    fallosSeguidos = 0;
    cambiarFase(LISTO, ahora);
  } else {
    fallo(ahora);
  }
}

static void procesarLinea(uint32_t ahora) {
  if (MODO_DEPURACION) Serial.println(linea);
  if (esperandoCuerpo) {
    esperandoCuerpo = false;
    mensajeRecibido(remitente, linea);
    return;
  }
  if (strncmp_P(linea, PSTR("+CMT:"), 5) == 0) {  // +CMT: "+52...","","26/10/08,10:00:00-24"
    const char *a = strchr(linea, '"');
    const char *b = a ? strchr(a + 1, '"') : NULL;
    if (a && b && (size_t)(b - a - 1) < sizeof(remitente)) {
      memcpy(remitente, a + 1, b - a - 1);
      remitente[b - a - 1] = '\0';
      esperandoCuerpo = true;
    }
    return;
  }
  bool ok = strcmp_P(linea, PSTR("OK")) == 0;
  bool error = strncmp_P(linea, PSTR("ERROR"), 5) == 0 || strncmp_P(linea, PSTR("+CMS ERROR"), 10) == 0;
  if (strncmp_P(linea, PSTR("+CSQ:"), 5) == 0) {
    estado.senal = (uint8_t)atoi(linea + 5);
    return;
  }
  switch (fase) {
    case ESPERA_OK:
      // La SIM vacía responde ERROR a CMGDA: también vale.
      if (ok || (error && pasoInicio == PASOS_INICIO - 1)) {
        fallosSeguidos = 0;
        if (consultandoSenal) {
          consultandoSenal = false;
          ultimaSenal = ahora;
          cambiarFase(LISTO, ahora);
        } else {
          pasoInicio++;
          cambiarFase(pasoInicio < PASOS_INICIO ? INICIANDO : LISTO, ahora);
        }
      } else if (error) {
        consultandoSenal = false;
        fallo(ahora);
      }
      break;
    case ESPERA_ENVIO:
      if (strncmp_P(linea, PSTR("+CMGS:"), 6) == 0) envioConfirmado = true;
      else if (ok && envioConfirmado) terminarEnvio(true, ahora);
      else if (error) terminarEnvio(false, ahora);
      break;
    default:
      break;
  }
}

void gsmIniciar() {
  modem.begin(9600);
}

bool gsmEncolar(const Envio &envio) {
  if (colaCantidad >= TAMANO_COLA) return false;
  cola[(colaInicio + colaCantidad) % TAMANO_COLA] = envio;
  colaCantidad++;
  return true;
}

bool gsmRecibiendo() {
  return largo > 0 || esperandoCuerpo || modem.available();
}

bool gsmListo() {
  return fase == LISTO || fase == ESPERA_PROMPT || fase == ESPERA_ENVIO;
}

static void empezarEnvio(uint32_t ahora) {
  const Envio &envio = cola[colaInicio];
  const char *numero = mensajeNumero(envio.destino);
  size_t n = numero ? mensajeConstruir(envio, salida, sizeof(salida), ahora) : 0;
  if (n == 0) {  // nada que enviar: se descarta
    colaInicio = (colaInicio + 1) % TAMANO_COLA;
    colaCantidad--;
    return;
  }
  modem.print(F("AT+CMGS=\""));
  modem.print(numero);
  modem.print(F("\"\r"));
  envioConfirmado = false;
  cambiarFase(ESPERA_PROMPT, ahora);
}

void gsmAtender(uint32_t ahora) {
  while (modem.available()) {
    char c = modem.read();
    if (fase == ESPERA_PROMPT && c == '>') {  // el prompt no termina en salto de línea
      modem.print(salida);
      modem.write(26);  // Ctrl+Z
      cambiarFase(ESPERA_ENVIO, ahora);
      continue;
    }
    if (c == '\r') continue;
    if (c == '\n') {
      linea[largo] = '\0';
      if (largo > 0) procesarLinea(ahora);
      largo = 0;
    } else if (largo < sizeof(linea) - 1) {
      linea[largo++] = c;
    }
  }

#if MODO_DEPURACION
  while (Serial.available()) modem.write(Serial.read());
#endif

  uint32_t enFase = ahora - desdeFase;
  switch (fase) {
    case INICIANDO: {
      char comando[24];
      strcpy_P(comando, (const char *)pgm_read_ptr(&SECUENCIA_INICIO[pasoInicio]));
      modem.println(comando);
      cambiarFase(ESPERA_OK, ahora);
      break;
    }
    case ESPERA_OK:
      if (enFase >= ESPERA_RESPUESTA) {
        consultandoSenal = false;
        fallo(ahora);
      }
      break;
    case LISTO:
      if (colaCantidad > 0) {
        empezarEnvio(ahora);
      } else if (senalPendiente || ahora - ultimaSenal >= PERIODO_SENAL) {
        senalPendiente = false;
        modem.println(F("AT+CSQ"));
        consultandoSenal = true;
        ultimaSenal = ahora;
        cambiarFase(ESPERA_OK, ahora);
      }
      break;
    case ESPERA_PROMPT:
      if (enFase >= ESPERA_PROMPT_MAX) {
        modem.write(27);  // ESC cancela el envío
        terminarEnvio(false, ahora);
      }
      break;
    case ESPERA_ENVIO:
      if (enFase >= ESPERA_ENVIO_MAX) terminarEnvio(false, ahora);
      break;
    case PAUSA:
      if (enFase >= duracionPausa) cambiarFase(faseTrasPausa, ahora);
      break;
  }
}
