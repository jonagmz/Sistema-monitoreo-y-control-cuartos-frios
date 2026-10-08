#pragma once
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <strings.h>
#include <cmath>
#include <string>
#include <deque>
#include <map>
#include <vector>
#define HIGH 1
#define LOW 0
#define OUTPUT 1
class __FlashStringHelper;
#define F(s) (reinterpret_cast<const __FlashStringHelper *>(s))
extern unsigned long fakeMillis;
inline unsigned long millis() { return fakeMillis++; }
inline void delay(unsigned long ms) { fakeMillis += ms; }
extern std::map<int, int> pinState; extern std::map<int, int> pinMode_;
inline void digitalWrite(int p, int v) { pinState[p] = v; }
inline void pinMode(int p, int m) { pinMode_[p] = m; }
inline char *dtostrf(double v, signed char w, unsigned char prec, char *out) { snprintf(out, 16, "%*.*f", w, prec, v); return out; }
struct Printer {
  std::string out;
  void print(const char *s) { out += s; } void print(const __FlashStringHelper *s) { out += (const char *)s; }
  void print(char c) { out += c; } void print(int v) { out += std::to_string(v); }
  template <class T> void println(T v) { print(v); out += "\r\n"; } void println() { out += "\r\n"; }
  void write(uint8_t c) { out += (char)c; } void begin(long) {}
  std::deque<char> in; int available() { return (int)in.size(); } int read() { if (in.empty()) return -1; char c = in.front(); in.pop_front(); return c; }
};
extern Printer Serial;
