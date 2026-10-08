// SIM800L simulado: responde a los comandos AT como el módulo real y guarda los SMS enviados.
#pragma once
#include "Arduino.h"
#include <vector>

struct SmsEnviado { std::string numero, texto; };

struct SoftwareSerial {
  std::deque<char> entrada;
  std::string pendiente;           // lo que el Arduino está escribiendo al módulo
  std::vector<SmsEnviado> enviados;
  std::vector<std::string> comandos;
  bool enModoTexto = false;        // tras AT+CMGS, esperando el texto y Ctrl+Z
  std::string numeroActual;
  bool mudo = false;               // simula un módulo que no responde
  int senal = 21;

  static SoftwareSerial *instancia;
  SoftwareSerial(int, int) { instancia = this; }
  void begin(long) {}
  int available() { return (int)entrada.size(); }
  int read() { if (entrada.empty()) return -1; char c = entrada.front(); entrada.pop_front(); return (uint8_t)c; }
  void responder(const std::string &s) { if (!mudo) for (char c : s) entrada.push_back(c); }
  void recibirSms(const std::string &numero, const std::string &texto) {
    responder("\r\n+CMT: \"" + numero + "\",\"\",\"26/10/08,10:00:00-24\"\r\n" + texto + "\r\n");
  }
  void escribir(char c) {
    if (enModoTexto) {
      if (c == 26) {
        enviados.push_back({numeroActual, pendiente});
        pendiente.clear();
        enModoTexto = false;
        responder("\r\n+CMGS: " + std::to_string(enviados.size()) + "\r\n\r\nOK\r\n");
      } else if (c == 27) {
        pendiente.clear();
        enModoTexto = false;
      } else {
        pendiente += c;
      }
      return;
    }
    if (c == '\n') return;
    if (c != '\r') { pendiente += c; return; }
    std::string cmd = pendiente;
    pendiente.clear();
    comandos.push_back(cmd);
    if (cmd.rfind("AT+CMGS=\"", 0) == 0) {
      numeroActual = cmd.substr(9, cmd.size() - 10);
      enModoTexto = true;
      responder("\r\n> ");
    } else if (cmd == "AT+CSQ") {
      responder("\r\n+CSQ: " + std::to_string(senal) + ",0\r\n\r\nOK\r\n");
    } else if (cmd.rfind("AT", 0) == 0) {
      responder("\r\nOK\r\n");
    }
  }
  void print(const char *s) { while (*s) escribir(*s++); }
  void print(const __FlashStringHelper *s) { print((const char *)s); }
  void println(const char *s) { print(s); escribir('\r'); escribir('\n'); }
  void println(const __FlashStringHelper *s) { println((const char *)s); }
  void write(uint8_t c) { escribir((char)c); }
};
