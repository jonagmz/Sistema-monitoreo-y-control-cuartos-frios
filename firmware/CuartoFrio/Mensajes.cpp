#include "Mensajes.h"
#include "Ajustes.h"
#include "Alarmas.h"
#include "Configuracion.h"
#include "Control.h"
#include "Estado.h"
#include "Gsm.h"
#include "Historial.h"
#include <stdarg.h>

static const char *const AUTORIZADOS[] = {NUMEROS_AUTORIZADOS};
static const char *const ALERTAS[] = {NUMEROS_ALERTAS};
static const uint8_t NUM_ALERTAS = sizeof(ALERTAS) / sizeof(ALERTAS[0]);

static char remitente[20];

enum CodigoError : uint8_t { ER_PIN, ER_CMD, ER_VAL };

// ---------------------------------------------------------------- números

static bool mismoNumero(const char *a, const char *b) {
  size_t la = strlen(a), lb = strlen(b);
  return la >= 10 && lb >= 10 && strcmp(a + la - 10, b + lb - 10) == 0;
}

static bool autorizado(const char *numero) {
  for (const char *n : AUTORIZADOS)
    if (mismoNumero(numero, n)) return true;
  return false;
}

const char *mensajeNumero(uint8_t destino) {
  if (destino == DESTINO_REMITENTE) return remitente[0] ? remitente : NULL;
  return destino < NUM_ALERTAS ? ALERTAS[destino] : NULL;
}

void avisarAlertas(char tipo, uint8_t param) {
  for (uint8_t i = 0; i < NUM_ALERTAS; i++) gsmEncolar({tipo, i, param, 0});
}

static void responder(char tipo, uint8_t param = 0, uint8_t param2 = 0) {
  gsmEncolar({tipo, DESTINO_REMITENTE, param, param2});
}

// ---------------------------------------------------------------- comandos

// Devuelve el siguiente token (separado por espacios) y avanza el cursor. Modifica el texto.
static char *siguienteToken(char *&cursor) {
  while (*cursor == ' ') cursor++;
  if (!*cursor) return NULL;
  char *inicio = cursor;
  while (*cursor && *cursor != ' ') cursor++;
  if (*cursor) *cursor++ = '\0';
  return inicio;
}

static void comandoSet(char *cursor) {
  Ajustes nuevos = ajustes;
  bool alguno = false;
  for (char *token = siguienteToken(cursor); token; token = siguienteToken(cursor)) {
    char *igual = strchr(token, '=');
    if (!igual) return responder('X', ER_VAL);
    *igual = '\0';
    if (!ajustesAplicar(nuevos, token, igual + 1)) return responder('X', ER_VAL);
    alguno = true;
  }
  if (!alguno || !ajustesCoherentes(nuevos)) return responder('X', ER_VAL);
  ajustes = nuevos;  // todo o nada
  ajustesGuardar();
  responder('C');
}

void mensajeRecibido(const char *numero, const char *texto) {
  if (!autorizado(numero)) return;  // a desconocidos no se les responde nada
  char copia[100];
  strncpy(copia, texto, sizeof(copia) - 1);
  copia[sizeof(copia) - 1] = '\0';
  char *cursor = copia;

  char *t = siguienteToken(cursor);
  if (!t || strcasecmp_P(t, PSTR("CF2")) != 0) return;  // no es para el equipo
  strncpy(remitente, numero, sizeof(remitente) - 1);
  remitente[sizeof(remitente) - 1] = '\0';

  if (strlen(PIN_SEGURIDAD) > 0) {
    t = siguienteToken(cursor);
    if (!t || strcmp(t, PIN_SEGURIDAD) != 0) return responder('X', ER_PIN);
  }
  t = siguienteToken(cursor);
  if (!t) return responder('X', ER_CMD);

  if (!strcasecmp_P(t, PSTR("INFO"))) {
    responder('R');
  } else if (!strcasecmp_P(t, PSTR("CFG"))) {
    responder('C');
  } else if (!strcasecmp_P(t, PSTR("SET"))) {
    comandoSet(cursor);
  } else if (!strcasecmp_P(t, PSTR("ON")) || !strcasecmp_P(t, PSTR("OFF"))) {
    ajustes.en = !strcasecmp_P(t, PSTR("ON"));
    if (ajustes.en) controlDesbloquear();  // alguien revisó el equipo y lo vuelve a encender
    ajustesGuardar();
    responder('C');
  } else if (!strcasecmp_P(t, PSTR("ACK"))) {
    alarmasReconocer();
    responder('R');
  } else if (!strcasecmp_P(t, PSTR("HIST"))) {
    char *h = siguienteToken(cursor);
    int horas = h ? atoi(h) : 48;
    horas = constrain(horas, 1, 255);
    uint8_t paginas = historialPaginas((uint8_t)horas);
    if (paginas == 0) paginas = 1;  // una página vacía para que la app sepa que no hay datos
    for (uint8_t p = 1; p <= paginas; p++) responder('H', p, (uint8_t)horas);
  } else {
    responder('X', ER_CMD);
  }
}

// ---------------------------------------------------------------- mensajes salientes

static size_t agregar(char *destino, size_t n, size_t tamano, const char *formato, ...) {
  if (n >= tamano) return n;
  va_list args;
  va_start(args, formato);
  int escritos = vsnprintf(destino + n, tamano - n, formato, args);
  va_end(args);
  return escritos < 0 ? n : (n + escritos < tamano ? n + escritos : tamano - 1);
}

static size_t agregarDecimas(char *destino, size_t n, size_t tamano, char clave, int16_t decimas) {
  n = agregar(destino, n, tamano, ";%c", clave);
  if (decimas == SIN_DATO) return agregar(destino, n, tamano, "--");
  return n + escribirDecimas(destino + n, tamano - n, decimas);
}

size_t mensajeConstruir(const Envio &envio, char *destino, size_t tamano, uint32_t ahora) {
  size_t n = 0;
  switch (envio.tipo) {
    case 'R':
      n = agregar(destino, n, tamano, "CF2;R");
      n = agregarDecimas(destino, n, tamano, 'T', estado.temp);
      n = agregar(destino, n, tamano, ";H");
      n = estado.hum == SIN_DATO ? agregar(destino, n, tamano, "--") : agregar(destino, n, tamano, "%d", (estado.hum + 5) / 10);
      n = agregarDecimas(destino, n, tamano, 'C', estado.caliente);
      n = agregar(destino, n, tamano, ";P%u;E%u;A%X", estado.potencia, ajustes.en, estado.alarmas);
      n = agregarDecimas(destino, n, tamano, 'S', ajustes.sp);
      n = agregar(destino, n, tamano, ";Q%u;U%lu;B%u", estado.senal, (unsigned long)estado.minutosEncendido, estado.bloqueoTermico);
      return n;
    case 'C':
      n = agregar(destino, n, tamano, "CF2;C;");
      n += ajustesEscribir(ajustes, destino + n, tamano - n);
      return agregar(destino, n, tamano, ";M%c", MODO_SALIDA == SALIDA_RELES ? 'R' : 'P');
    case 'H':
      return historialPagina(destino, tamano, envio.param, envio.param2, ahora);
    case 'E':
      return agregar(destino, n, tamano, "CF2;E;INICIO;%s", envio.param == EVENTO_INICIO_LUZ ? "LUZ" : "REINICIO");
    case 'X': {
      static const char *const CODIGOS[] = {"PIN", "CMD", "VAL"};
      return agregar(destino, n, tamano, "CF2;X;%s", CODIGOS[envio.param % 3]);
    }
  }
  return 0;
}
