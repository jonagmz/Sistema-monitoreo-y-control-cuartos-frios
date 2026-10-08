#include "Ajustes.h"
#include <EEPROM.h>
#include <stddef.h>

Ajustes ajustes;

static const uint8_t VERSION_AJUSTES = 2;
static const int DIRECCION = 0;

enum Formato : uint8_t { DECIMAS16, ENTERO8, ENTERO16, DECIMAS_U16 };

struct Clave {
  char nombre[3];
  Formato formato;
  uint8_t posicion;
  int16_t minimo, maximo;  // en las unidades guardadas (décimas cuando corresponde)
};

// Una línea por ajuste: nombre, formato, campo, rango.
static const Clave CLAVES[] PROGMEM = {
    {"SP", DECIMAS16, offsetof(Ajustes, sp), -200, 300},
    {"TL", DECIMAS16, offsetof(Ajustes, tl), -300, 500},
    {"TH", DECIMAS16, offsetof(Ajustes, th), -300, 500},
    {"HL", ENTERO8, offsetof(Ajustes, hl), 0, 100},
    {"HH", ENTERO8, offsetof(Ajustes, hh), 0, 100},
    {"AD", ENTERO8, offsetof(Ajustes, ad), 0, 120},
    {"RI", ENTERO16, offsetof(Ajustes, ri), 0, 1440},
    {"RA", ENTERO8, offsetof(Ajustes, ra), 0, 48},
    {"HI", ENTERO8, offsetof(Ajustes, hi), 5, 60},
    {"PM", ENTERO8, offsetof(Ajustes, pm), 0, 100},
    {"PC", ENTERO8, offsetof(Ajustes, pc), 40, 90},
    {"KP", DECIMAS_U16, offsetof(Ajustes, kp), 0, 2000},
    {"KI", DECIMAS_U16, offsetof(Ajustes, ki), 0, 500},
    {"KD", DECIMAS_U16, offsetof(Ajustes, kd), 0, 2000},
    {"EN", ENTERO8, offsetof(Ajustes, en), 0, 1},
};
static const uint8_t NUM_CLAVES = sizeof(CLAVES) / sizeof(CLAVES[0]);

static void porDefecto(Ajustes &a) {
  a = {};
  a.version = VERSION_AJUSTES;
  a.sp = 40; a.tl = 10; a.th = 80;
  a.hl = 60; a.hh = 95;
  a.ad = 10; a.ri = 60; a.ra = 4; a.hi = 15;
  a.pm = 100; a.pc = 65;
  a.kp = 200; a.ki = 10; a.kd = 0;
  a.en = 0;  // arranca apagado hasta que alguien lo configure
}

static uint8_t crc8(const Ajustes &a) {
  const uint8_t *p = (const uint8_t *)&a;
  uint8_t crc = 0;
  for (size_t i = 0; i < offsetof(Ajustes, crc); i++) {
    crc ^= p[i];
    for (uint8_t b = 0; b < 8; b++) crc = (crc & 0x80) ? (crc << 1) ^ 0x07 : crc << 1;
  }
  return crc;
}

bool ajustesCoherentes(const Ajustes &a) {
  for (uint8_t i = 0; i < NUM_CLAVES; i++) {
    Clave c;
    memcpy_P(&c, &CLAVES[i], sizeof(c));
    const uint8_t *campo = (const uint8_t *)&a + c.posicion;
    int32_t v = (c.formato == ENTERO8) ? *campo : (c.formato == DECIMAS16) ? *(const int16_t *)campo : *(const uint16_t *)campo;
    if (v < c.minimo || v > c.maximo) return false;
  }
  return a.sp % 5 == 0 && a.tl < a.sp && a.sp < a.th && a.hl < a.hh && (a.ri == 0 || a.ri >= 5);
}

bool ajustesCargar() {
  EEPROM.get(DIRECCION, ajustes);
  if (ajustes.version == VERSION_AJUSTES && ajustes.crc == crc8(ajustes) && ajustesCoherentes(ajustes)) return false;
  porDefecto(ajustes);
  ajustesGuardar();
  return true;
}

void ajustesGuardar() {
  ajustes.crc = crc8(ajustes);
  EEPROM.put(DIRECCION, ajustes);  // put solo reescribe los bytes que cambiaron
}

// Convierte "4", "-4.5" o "12.25" a décimas (se trunca a una decimal).
static bool leerDecimas(const char *texto, int32_t &decimas) {
  bool negativo = *texto == '-';
  if (negativo) texto++;
  if (!isdigit(*texto)) return false;
  int32_t entero = 0;
  while (isdigit(*texto)) {
    entero = entero * 10 + (*texto++ - '0');
    if (entero > 30000) return false;
  }
  int32_t decimal = 0;
  if (*texto == '.') {
    texto++;
    if (!isdigit(*texto)) return false;
    decimal = *texto - '0';
    while (isdigit(*texto)) texto++;
  }
  if (*texto) return false;
  decimas = (entero * 10 + decimal) * (negativo ? -1 : 1);
  return true;
}

bool ajustesAplicar(Ajustes &a, const char *clave, const char *valor) {
  for (uint8_t i = 0; i < NUM_CLAVES; i++) {
    Clave c;
    memcpy_P(&c, &CLAVES[i], sizeof(c));
    if (strcasecmp(clave, c.nombre) != 0) continue;
    int32_t decimas;
    if (!leerDecimas(valor, decimas)) return false;
    bool enDecimas = c.formato == DECIMAS16 || c.formato == DECIMAS_U16;
    if (!enDecimas) {
      if (decimas % 10 != 0) return false;  // los enteros no admiten decimales
      decimas /= 10;
    }
    if (decimas < c.minimo || decimas > c.maximo) return false;
    uint8_t *campo = (uint8_t *)&a + c.posicion;
    if (c.formato == ENTERO8) *campo = (uint8_t)decimas;
    else if (c.formato == DECIMAS16) *(int16_t *)campo = (int16_t)decimas;
    else *(uint16_t *)campo = (uint16_t)decimas;
    return true;
  }
  return false;
}

size_t escribirDecimas(char *destino, size_t tamano, int16_t decimas) {
  int32_t v = decimas;
  const char *signo = v < 0 ? "-" : "";
  if (v < 0) v = -v;
  int n = snprintf(destino, tamano, "%s%ld.%ld", signo, (long)(v / 10), (long)(v % 10));
  return n < 0 ? 0 : ((size_t)n < tamano ? (size_t)n : tamano - 1);
}

size_t ajustesEscribir(const Ajustes &a, char *destino, size_t tamano) {
  size_t n = 0;
  for (uint8_t i = 0; i < NUM_CLAVES && n + 8 < tamano; i++) {
    Clave c;
    memcpy_P(&c, &CLAVES[i], sizeof(c));
    const uint8_t *campo = (const uint8_t *)&a + c.posicion;
    if (i > 0) destino[n++] = ';';
    destino[n++] = c.nombre[0];
    destino[n++] = c.nombre[1];
    if (c.formato == DECIMAS16) {
      n += escribirDecimas(destino + n, tamano - n, *(const int16_t *)campo);
    } else if (c.formato == DECIMAS_U16) {
      uint16_t v = *(const uint16_t *)campo;  // KP/KI/KD: sin ".0" si es entero, para ahorrar caracteres
      n += snprintf(destino + n, tamano - n, v % 10 ? "%u.%u" : "%u", v / 10, v % 10);
    } else {
      n += snprintf(destino + n, tamano - n, "%u", c.formato == ENTERO8 ? *campo : *(const uint16_t *)campo);
    }
  }
  destino[n] = '\0';
  return n;
}
