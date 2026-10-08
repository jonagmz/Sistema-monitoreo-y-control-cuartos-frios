// Simulación del firmware en la computadora: imita el Arduino, el SIM800L, el DHT, la LCD y la EEPROM
// y comprueba el comportamiento real del sketch. Ejecutar con ./correr.sh
#include "Arduino.h"
int wdtResets = 0, wdtEnabled = 0; unsigned long fakeMillis = 0; std::map<int,int> pinState, pinMode_; Printer Serial;
#include "EEPROM.h"
EEPROMClass EEPROM; float simTemp = 4.0, simHum = 85;
#define PRUEBAS  // usa config.example.h aunque exista un config.h local
#include "../cuartoFrio/cuartoFrio.ino"

int fallos = 0;
#define CHECK(c, msg) do { bool ok_ = (c); printf("  [%s] %s\n", ok_ ? "OK " : "FALLA", msg); if (!ok_) fallos++; } while (0)
struct Sms { std::string num, txt; };
std::vector<Sms> smsEnviados() {
  std::vector<Sms> v; const std::string &o = gsm.out; size_t p = 0;
  while ((p = o.find("AT+CMGS=\"", p)) != std::string::npos) {
    size_t q = o.find('"', p + 9); size_t t0 = o.find("\r\n", q) + 2; size_t t1 = o.find('\x1A', t0);
    v.push_back({o.substr(p + 9, q - p - 9), o.substr(t0, t1 - t0)}); p = t1;
  }
  return v;
}
void correr(unsigned long ms) { unsigned long fin = fakeMillis + ms; while (fakeMillis < fin) { loop(); fakeMillis += 50; } }
void recibirSms(const std::string &de, const std::string &txt) { gsm.feed("+CMT: \"" + de + "\",\"\",\"26/10/08,10:00:00-24\"\r\n" + txt + "\r\n"); correr(500); }
bool relesEncendidos() { for (int p : {3,4,5,6}) if (pinState[p] != LOW) return false; return true; }
bool relesApagados() { for (int p : {3,4,5,6}) if (pinState[p] != HIGH) return false; return true; }

int main() {
  const std::string AUT = "+520000000000", AUT_SIN_LADA = "0000000000", INTRUSO = "+529999999999";
  puts("1) Arranque");
  setup(); correr(3000);
  CHECK(!config.sistemaEncendido, "arranca con el sistema apagado si la EEPROM está vacía");
  CHECK(relesApagados(), "relés apagados al arrancar");
  CHECK(pinMode_.count(0) == 0 && pinMode_.count(1) == 0 && pinMode_.count(2) == 0 && pinState.count(2) == 0,
        "no toca los pines 0, 1 (serie) ni 2 (sensor)");
  CHECK(lcd.rows[3] == "Sistema APAGADO     ", "LCD muestra 'Sistema APAGADO'");
  CHECK(wdtEnabled && wdtResets > 0, "watchdog activado y alimentado en cada vuelta del loop");

  puts("2) SMS de un número NO autorizado");
  size_t antes = smsEnviados().size();
  recibirSms(INTRUSO, "@*2*6*80*95*1");
  CHECK(!config.sistemaEncendido, "ignora la orden de encender");
  CHECK(smsEnviados().size() == antes, "no responde al intruso");

  puts("3) Configuración desde número autorizado (sin +52)");
  recibirSms(AUT_SIN_LADA, "@*2*6*80*95*1");
  CHECK(config.sistemaEncendido && config.tempMin == 2 && config.tempMax == 6 && config.humMin == 80 && config.humMax == 95, "aplica la configuración");
  auto s = smsEnviados();
  CHECK(s.size() == antes + 1 && s.back().num == AUT_SIN_LADA, "responde con un reporte al remitente");
  printf("     reporte: %s\n", s.back().txt.c_str());
  CHECK(s.back().txt == "4.0,85.0,Cuarto frio no. 1,0,0,0,0,0,0,1,2,6,80,95", "formato compatible (7 campos originales + 7 nuevos)");

  puts("4) Temperatura alta -> espera el tiempo mínimo de apagado del compresor y enfría");
  simTemp = 8.0; correr(5000);
  CHECK(relesApagados(), "no arranca el compresor antes de 3 minutos desde el arranque");
  s = smsEnviados(); CHECK(s.back().num == AUT && s.back().txt.find(",0,1,0,0,") != std::string::npos, "envía SMS de alarma de temperatura alta al momento");
  correr(3UL * 60 * 1000);
  CHECK(relesEncendidos(), "enciende los relés tras el tiempo mínimo");
  CHECK(lcd.rows[3].rfind("ALARMA T+", 0) == 0, "LCD muestra 'ALARMA T+'");

  puts("5) Histéresis: entre mínimo y máximo mantiene el estado");
  simTemp = 4.0; correr(5000);
  CHECK(relesEncendidos(), "sigue enfriando entre 2 y 6 °C");
  simTemp = 1.5; correr(5000);
  CHECK(relesApagados(), "apaga al bajar del mínimo");
  simTemp = 7.0; correr(30000);
  CHECK(relesApagados(), "no vuelve a arrancar enseguida (protección del compresor)");
  correr(3UL * 60 * 1000);
  CHECK(relesEncendidos(), "vuelve a arrancar pasados 3 minutos");

  puts("6) Límite de SMS de alarma");
  simTemp = 4.0; correr(5000); size_t n0 = smsEnviados().size();
  simTemp = 9.0; correr(5000); simTemp = 4.0; correr(5000); simTemp = 9.0; correr(5000);
  CHECK(smsEnviados().size() - n0 <= 1, "como mucho 1 SMS de alarma cada 5 minutos aunque la alarma cambie");

  puts("7) Configuración inválida");
  Config previa = config; n0 = smsEnviados().size();
  recibirSms(AUT, "@*6*2*80*95*1");
  CHECK(config.tempMin == previa.tempMin && config.tempMax == previa.tempMax, "rechaza mínimo mayor que máximo");
  s = smsEnviados(); CHECK(s.size() == n0 + 1 && s.back().txt.rfind("ERROR", 0) == 0, "responde con ERROR");
  recibirSms(AUT, "@*2*6*80*95*null");
  CHECK(config.sistemaEncendido, "rechaza el 'null' que mandaba la app vieja si no se tocaba el switch");
  recibirSms(AUT, "@*-18*-15*60*90*1");
  CHECK(config.tempMin == -18 && config.tempMax == -15, "acepta temperaturas negativas (congelación)");
  recibirSms(AUT, "@*2*6*80*95*1");

  puts("8) Fallo del sensor");
  simTemp = NAN; correr(10000);
  CHECK(alarmas & ALARMA_SENSOR, "detecta el fallo tras 3 lecturas fallidas");
  CHECK(lcd.rows[0].rfind("ERROR DE SENSOR", 0) == 0, "LCD muestra 'ERROR DE SENSOR'");
  correr(6UL * 60 * 1000);
  s = smsEnviados(); CHECK(s.back().txt.rfind("ND,ND,", 0) == 0, "el reporte envía ND en lugar de valores falsos");
  simTemp = 4.0; correr(5000);
  CHECK(!(alarmas & ALARMA_SENSOR), "se recupera cuando vuelve la lectura");

  puts("9) INFO y reporte periódico");
  n0 = smsEnviados().size(); recibirSms(AUT, "info");
  CHECK(smsEnviados().size() == n0 + 1, "responde a INFO");
  correr(16UL * 60 * 1000);
  CHECK(smsEnviados().size() >= n0 + 2, "envía el reporte periódico cada 15 minutos");

  puts("10) Corte de luz: la configuración sobrevive al reinicio");
  config = {}; enfriando = false; pinState.clear(); setup(); correr(3000);
  CHECK(config.sistemaEncendido && config.tempMin == 2 && config.tempMax == 6, "recupera la configuración de la EEPROM");

  printf("\n%s (%d fallos)\n", fallos ? "HAY FALLOS" : "TODO OK", fallos);
  return fallos != 0;
}
