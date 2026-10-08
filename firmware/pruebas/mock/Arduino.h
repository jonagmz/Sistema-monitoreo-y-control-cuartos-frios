// Simulación mínima del entorno Arduino para ejecutar el firmware en la computadora.
#pragma once
#include <cctype>
#include <climits>
#include <cmath>
#include <cstdarg>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <deque>
#include <map>
#include <string>
#include <strings.h>

#define HIGH 1
#define LOW 0
#define OUTPUT 1
#define A0 14
#define PROGMEM
#define PSTR(s) (s)
class __FlashStringHelper;
#define F(s) (reinterpret_cast<const __FlashStringHelper *>(s))
#define memcpy_P memcpy
#define strcpy_P strcpy
#define strcmp_P strcmp
#define strncmp_P strncmp
#define strcasecmp_P strcasecmp
#define pgm_read_byte(p) (*(const uint8_t *)(p))
#define pgm_read_ptr(p) (*(void *const *)(p))
#define constrain(x, a, b) ((x) < (a) ? (a) : ((x) > (b) ? (b) : (x)))
#ifndef min
#define min(a, b) ((a) < (b) ? (a) : (b))
#endif

extern unsigned long simMillis;
inline unsigned long millis() { return simMillis; }
inline void delay(unsigned long ms) { simMillis += ms; }

extern std::map<int, int> simPines, simModos, simAnalogico;
inline void digitalWrite(int p, int v) { simPines[p] = v; }
inline void pinMode(int p, int m) { simModos[p] = m; }
inline void analogWrite(int p, int v) { simPines[p] = v; }
inline int analogRead(int p) { return simAnalogico[p]; }

struct SimSerie {
  std::string salida;
  std::deque<char> entrada;
  void begin(long) {}
  int available() { return (int)entrada.size(); }
  int read() { if (entrada.empty()) return -1; char c = entrada.front(); entrada.pop_front(); return (uint8_t)c; }
  void print(const char *s) { salida += s; }
  void print(const __FlashStringHelper *s) { salida += (const char *)s; }
  void print(char c) { salida += c; }
  void print(int v) { salida += std::to_string(v); }
  void print(unsigned v) { salida += std::to_string(v); }
  template <class T> void println(T v) { print(v); salida += "\r\n"; }
  void println() { salida += "\r\n"; }
  void write(uint8_t c) { salida += (char)c; }
};
extern SimSerie Serial;
