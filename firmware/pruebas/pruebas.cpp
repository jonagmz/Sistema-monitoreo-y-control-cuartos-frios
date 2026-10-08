// Pruebas del firmware en la computadora: ejecuta los módulos reales con el hardware simulado.
// Ejecutar con ./correr.sh
#include "Arduino.h"
#include "EEPROM.h"
#include "SoftwareSerial.h"
#include "DHT.h"
#include "LiquidCrystal_I2C.h"
#include <vector>

unsigned long simMillis = 0;
std::map<int, int> simPines, simModos, simAnalogico;
SimSerie Serial;
SimEeprom EEPROM;
float simTemp = 4.0f, simHum = 80.0f;
int simLecturasDht = 0, simWatchdog = 0;
SoftwareSerial *SoftwareSerial::instancia = nullptr;
LiquidCrystal_I2C *LiquidCrystal_I2C::instancia = nullptr;

#include "../CuartoFrio/CuartoFrio.ino"
#include "../CuartoFrio/Configuracion.h"
#include "../CuartoFrio/Hal.h"

static int fallos = 0, total = 0;
#define CHECK(c, msg) do { total++; bool ok_ = (c); printf("  [%s] %s\n", ok_ ? "OK " : "FALLA", msg); if (!ok_) fallos++; } while (0)

static const std::string AUT = "+520000000001", AUT2 = "+520000000002", ALERTA2 = "+520000000009";
static const std::string INTRUSO = "+529999999999", PIN = "4321";

SoftwareSerial &modem() { return *SoftwareSerial::instancia; }
LiquidCrystal_I2C &lcd() { return *LiquidCrystal_I2C::instancia; }

void correr(unsigned long ms, unsigned long paso = 50) {
  unsigned long fin = simMillis + ms;
  while (simMillis < fin) { loop(); simMillis += paso; }
}
void sms(const std::string &de, const std::string &texto) { modem().recibirSms(de, texto); correr(2000); }
void cmd(const std::string &texto, const std::string &de = AUT) { sms(de, "CF2 " + PIN + " " + texto); }
size_t nEnviados() { return modem().enviados.size(); }
const SmsEnviado &ultimo() { static SmsEnviado vacio; return modem().enviados.empty() ? vacio : modem().enviados.back(); }
std::vector<SmsEnviado> desde(size_t n) { return std::vector<SmsEnviado>(modem().enviados.begin() + n, modem().enviados.end()); }
bool contiene(const std::string &s, const std::string &sub) { return s.find(sub) != std::string::npos; }
std::string campo(const std::string &msg, char clave) {  // valor de un campo "Kvalor" de un mensaje CF2
  size_t p = 0;
  while ((p = msg.find(';', p)) != std::string::npos) { p++; if (msg[p] == clave) { size_t f = msg.find(';', p); return msg.substr(p + 1, f == std::string::npos ? std::string::npos : f - p - 1); } }
  return "";
}

int ntcAdc(float c) {  // lectura del ADC que da un NTC de 10 kΩ (B 3950) a c grados
  double r = 10000.0 * exp(3950.0 * (1.0 / (c + 273.15) - 1.0 / 298.15));
  return (int)lround(1023.0 * r / (10000.0 + r));
}
void ladoCaliente(float c) { simAnalogico[A0] = ntcAdc(c); }

int celdasEncendidas() {
#if MODO_SALIDA == SALIDA_RELES
  int n = 0;
  for (int p : {3, 4, 5, 6}) n += simPines[p] == LOW;
  return n;
#else
  return simPines[9];
#endif
}

std::vector<uint8_t> base64url(const std::string &s) {
  static const std::string ALF = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
  std::vector<uint8_t> out; uint32_t acc = 0; int bits = 0;
  for (char c : s) { acc = acc << 6 | ALF.find(c); bits += 6; if (bits >= 8) { bits -= 8; out.push_back((acc >> bits) & 0xFF); } }
  return out;
}

void fase1(const char *rutaEeprom) {
#if MODO_SALIDA == SALIDA_RELES
  puts("\n=== Firmware v2 — salida por RELÉS ===");
#else
  puts("\n=== Firmware v2 — salida PWM ===");
#endif
  ladoCaliente(30);

  puts("1) Arranque");
  setup();
  correr(5000);
  auto avisos = modem().enviados;
  CHECK(avisos.size() == 2 && avisos[0].texto == "CF2;E;INICIO;LUZ" && avisos[0].numero == AUT && avisos[1].numero == ALERTA2,
        "avisa 'INICIO;LUZ' a los 2 números de alertas tras un corte de luz");
  CHECK(celdasEncendidas() == 0, "celdas apagadas al arrancar (el control arranca apagado)");
  CHECK(!simModos.count(2) && !simModos.count(0) && !simModos.count(1), "no configura como salida el pin del DHT ni los del puerto serie");
  CHECK(simWatchdog == 1, "watchdog activado");
  CHECK(lcd().filas[3].rfind("Control APAGADO", 0) == 0, "LCD: 'Control APAGADO'");
  CHECK(estado.senal == 21, "consulta la señal del módulo (CSQ 21)");
  CHECK(!hal::arranqueEnFrio(), "un reinicio sin corte de luz se distingue de un corte (marca en RAM)");

  puts("2) Seguridad");
  size_t n0 = nEnviados();
  sms(INTRUSO, "CF2 4321 ON");
  CHECK(nEnviados() == n0 && !ajustes.en, "ignora y no responde a un número no autorizado");
  sms(AUT, "CF2 ON");
  CHECK(ultimo().texto == "CF2;X;PIN" && !ajustes.en, "sin PIN: responde X;PIN y no obedece");
  sms(AUT, "CF2 1111 ON");
  CHECK(ultimo().texto == "CF2;X;PIN", "PIN equivocado: X;PIN");
  cmd("BAILA");
  CHECK(ultimo().texto == "CF2;X;CMD", "comando desconocido: X;CMD");
  n0 = nEnviados();
  sms(AUT, "Hola, ¿cómo va todo?");
  CHECK(nEnviados() == n0, "ignora SMS normales aunque vengan de un número autorizado");
  sms("6670000002", "cf2 4321 info");  // sin +52 y en minúsculas
  CHECK(ultimo().numero == "6670000002" ? false : true, "compara los últimos 10 dígitos del número");

  puts("3) Configuración");
  cmd("CFG");
  CHECK(ultimo().texto == "CF2;C;SP4.0;TL1.0;TH8.0;HL60;HH95;AD10;RI60;RA4;HI15;PM100;PC65;KP20;KI1;KD0;EN0;MR" ||
        ultimo().texto == "CF2;C;SP4.0;TL1.0;TH8.0;HL60;HH95;AD10;RI60;RA4;HI15;PM100;PC65;KP20;KI1;KD0;EN0;MP",
        "CFG devuelve los ajustes de fábrica");
  cmd("SET SP=3.5 TH=7 AD=1 RI=0 KI=0.5", AUT2);
  CHECK(ultimo().numero == AUT2 && contiene(ultimo().texto, "SP3.5;TL1.0;TH7.0") && contiene(ultimo().texto, "AD1;RI0") && contiene(ultimo().texto, "KI0.5"),
        "SET aplica varios valores y responde al remitente con la configuración");
  cmd("SET SP=9 TH=7");
  CHECK(ultimo().texto == "CF2;X;VAL" && ajustes.sp == 35, "SET incoherente (objetivo mayor que la alarma alta): X;VAL y no cambia nada");
  cmd("SET SP=3.3");
  CHECK(ultimo().texto == "CF2;X;VAL", "el objetivo va en pasos de 0.5 °C");
  cmd("SET TL=0 XX=1");
  CHECK(ultimo().texto == "CF2;X;VAL" && ajustes.tl == 10, "una clave desconocida invalida todo el SET (todo o nada)");
  cmd("SET HL=70.5");
  CHECK(ultimo().texto == "CF2;X;VAL", "los ajustes enteros no aceptan decimales");
  cmd("SET TL=-2.5 SP=-1");
  CHECK(ajustes.tl == -25 && ajustes.sp == -10, "acepta temperaturas negativas");
  cmd("SET TL=1 SP=3.5");

  puts("4) Control PID");
  simTemp = 9.0f;
  n0 = nEnviados();
  cmd("ON");
  CHECK(ajustes.en == 1 && contiene(ultimo().texto, "EN1"), "ON enciende el control");
  correr(8000);
#if MODO_SALIDA == SALIDA_RELES
  CHECK(celdasEncendidas() == 1, "con 5.5 °C por encima enciende la primera celda");
  correr(60000);
  CHECK(celdasEncendidas() == 2, "agrega una celda por minuto (no arranca las 4 de golpe)");
  correr(3 * 60000);
  CHECK(celdasEncendidas() == 4 && estado.potencia == 100, "llega a las 4 celdas (100 %)");
#else
  CHECK(simPines[9] > 0 && simPines[9] <= 20, "PWM: arranca con rampa suave");
  correr(60000);
  CHECK(simPines[9] == 100, "PWM: llega al 100 % en rampa");
#endif
  CHECK(estado.alarmas & AL_TEMP_ALTA, "alarma de temperatura alta tras el retardo de 1 min");
  auto nuevos = desde(n0);
  bool avisoAlarma = false;
  for (auto &s : nuevos) avisoAlarma |= s.numero == ALERTA2 && contiene(s.texto, ";A1;");
  CHECK(avisoAlarma, "avisa la alarma por SMS a los números de alertas");

  puts("5) Llega al objetivo");
  simTemp = 3.5f;
#if MODO_SALIDA == SALIDA_RELES
  correr(10000);
  CHECK(celdasEncendidas() == 3 && simPines[3] == HIGH, "quita una celda: apaga la que lleva más tiempo encendida (rotación)");
  correr(4 * 60000);
  CHECK(celdasEncendidas() == 0, "en el objetivo, sin acumulación integral, apaga todas");
#else
  correr(10000);
  CHECK(simPines[9] == 0, "PWM: en el objetivo baja la potencia");
#endif
  CHECK(!(estado.alarmas & AL_TEMP_ALTA), "la alarma se resuelve");

  puts("6) Protección del lado caliente");
  simTemp = 9.0f;
  correr(5 * 60000);
  ladoCaliente(60);
  correr(5 * 60000);
  CHECK(estado.potencia >= 45 && estado.potencia <= 55 && estado.caliente >= 595 && estado.caliente <= 602,
        "con el disipador a 60 °C (5 °C del límite) limita la potencia a ~50 % (la lectura del ADC tiene ±0.2 °C)");
  n0 = nEnviados();
  ladoCaliente(70);
  correr(20000);
  CHECK(celdasEncendidas() == 0 && estado.protegiendo, "a 70 °C (límite 65) apaga todas las celdas al momento");
  correr(130000);
  bool avisoCaliente = false;
  for (auto &s : desde(n0)) avisoCaliente |= contiene(s.texto, ";A21;") || contiene(s.texto, ";A20;") || contiene(s.texto, ";A1;");
  CHECK(estado.alarmas & AL_SOBRECALENTADO, "alarma de sobrecalentamiento");
  CHECK(avisoCaliente, "avisa por SMS");
  CHECK(lcd().filas[3].find("DISIPADOR") != std::string::npos || lcd().filas[3].find("ALARMA") != std::string::npos, "LCD muestra la alarma");
  ladoCaliente(55);
  correr(30000);
  CHECK(estado.protegiendo && celdasEncendidas() == 0, "no se rearma hasta bajar 15 °C del límite");
  ladoCaliente(45);
  correr(3 * 60000);
  CHECK(!estado.protegiendo && celdasEncendidas() > 0, "se rearma por debajo de 50 °C");
  simAnalogico[A0] = 1023;
  correr(10000);
  CHECK(estado.fallaCaliente && (estado.alarmas & AL_SENSOR_CALIENTE), "detecta el termistor desconectado");
  correr(4 * 60000);
  CHECK(estado.potencia <= 50, "sin sensor del lado caliente limita la potencia al 50 %");
  ladoCaliente(30);

  puts("6b) Bloqueo térmico (ventilador del disipador averiado)");
  correr(3 * 60000);
  CHECK(estado.alarmas & AL_SOBRECALENTADO, "la alarma de sobrecalentamiento se sostiene 15 min tras rearmarse (no parpadea)");
  for (int i = 0; i < 3 && !estado.bloqueoTermico; i++) {
    ladoCaliente(70);
    correr(20000);
    ladoCaliente(45);
    correr(3 * 60000);
  }
  CHECK(estado.bloqueoTermico, "3 disparos en menos de una hora: bloqueo térmico");
  bool avisoBloqueo = false;
  for (auto &s : modem().enviados) avisoBloqueo |= s.numero == ALERTA2 && contiene(s.texto, ";B1");
  correr(3 * 60000);
  for (auto &s : modem().enviados) avisoBloqueo |= s.numero == ALERTA2 && contiene(s.texto, ";B1");
  CHECK(avisoBloqueo, "avisa el bloqueo por SMS aunque la alarma ya estuviera activa");
  ladoCaliente(30);
  correr(5 * 60000);
  CHECK(celdasEncendidas() == 0 && estado.potencia == 0, "bloqueado: no vuelve a encender aunque el disipador se enfríe");
  CHECK(lcd().filas[3].rfind("BLOQUEO TERMICO", 0) == 0, "LCD: 'BLOQUEO TERMICO'");
  cmd("INFO");
  CHECK(campo(ultimo().texto, 'B') == "1" && contiene(ultimo().texto, ";A2"), "el reporte indica el bloqueo (B1) y la alarma");
  cmd("ON");
  correr(2 * 60000);
  CHECK(!estado.bloqueoTermico && celdasEncendidas() > 0, "ON quita el bloqueo y vuelve a enfriar");
  correr(16 * 60000);
  CHECK(!(estado.alarmas & AL_SOBRECALENTADO), "la alarma se resuelve 15 min después del último disparo");

  puts("7) Falla del sensor de ambiente");
  correr(3 * 60000);
  int potenciaAntes = estado.potencia;
  simTemp = NAN;
  correr(8000);
  CHECK(!estado.fallaAmbiente, "tolera lecturas fallidas sueltas");
  correr(10000);
  CHECK(estado.fallaAmbiente && (estado.alarmas & AL_SENSOR), "tras 5 lecturas fallidas: alarma de sensor");
  CHECK(estado.potencia == potenciaAntes, "mantiene la potencia que tenía");
  CHECK(lcd().filas[0].rfind("SIN SENSOR", 0) == 0, "LCD: 'SIN SENSOR AMBIENTE'");
  simTemp = 3.5f;
  correr(10000);
  CHECK(!estado.fallaAmbiente, "se recupera al volver la lectura");

  puts("8) Retardo, reconocimiento y recordatorio de alarmas");
  cmd("SET AD=10 RA=1");
  correr(10 * 60000);
  simHum = 98.0f;  // humedad alta: no afecta al control, así no interviene la falla de enfriamiento
  correr(9 * 60000);
  CHECK(!(estado.alarmas & AL_HUM_ALTA), "no alarma antes del retardo de 10 min (p. ej. puerta abierta)");
  correr(2 * 60000);
  CHECK(estado.alarmas & AL_HUM_ALTA, "alarma pasado el retardo");
  n0 = nEnviados();
  correr(61 * 60000, 200);
  bool recordatorio = false;
  for (auto &s : desde(n0)) recordatorio |= contiene(s.texto, "CF2;R") && contiene(s.texto, ";A4;");
  CHECK(recordatorio, "recordatorio a la hora si nadie la reconoce");
  cmd("ACK");
  CHECK(contiene(ultimo().texto, "CF2;R"), "ACK responde con el estado");
  n0 = nEnviados();
  correr(130 * 60000, 200);
  bool sinRecordatorio = true;
  for (auto &s : desde(n0)) sinRecordatorio &= !contiene(s.texto, ";A4;");
  CHECK(sinRecordatorio, "reconocida: no más recordatorios");
  simHum = 80.0f;

  puts("9) Falla de enfriamiento");
  simTemp = 9.0f;
  correr(30 * 60000, 200);
  CHECK(!(estado.alarmas & AL_FALLA_ENFRIAMIENTO), "a los 30 min con potencia máxima aún no es falla");
  correr(40 * 60000, 200);
  CHECK(estado.alarmas & AL_FALLA_ENFRIAMIENTO, "60 min al máximo sin bajar la temperatura: falla de enfriamiento");
  simTemp = 3.5f;
  correr(10 * 60000, 200);
  CHECK(!(estado.alarmas & AL_FALLA_ENFRIAMIENTO), "se resuelve al recuperar la temperatura");

  puts("10) Historial");
  for (int h = 0; h < 50; h++) {  // 50 h: más que la capacidad (48 h)
    simTemp = 3.0f + (h % 4) * 0.5f;
    simHum = 80.0f + h % 5;
    correr(60 * 60000, 500);
  }
  CHECK(historialCantidad() == historialCapacidad(), "el buffer da la vuelta y mantiene 192 muestras");
  n0 = nEnviados();
  cmd("HIST");
  correr(30000);
  auto paginas = desde(n0);
  CHECK(paginas.size() == 8, "HIST de 48 h: 8 SMS de 24 muestras");
  bool formato = true;
  std::vector<uint8_t> datos;
  for (size_t i = 0; i < paginas.size(); i++) {
    const std::string &t = paginas[i].texto;
    char esperado[24];
    snprintf(esperado, sizeof esperado, "CF2;H;%zu/8;I15;", i + 1);
    formato &= t.rfind(esperado, 0) == 0 && campo(t, 'N') == std::to_string(168 - 24 * i) && t.size() <= 160;
    auto b = base64url(t.substr(t.rfind(';') + 1));
    datos.insert(datos.end(), b.begin(), b.end());
  }
  CHECK(formato, "cada página: número, intervalo, muestras restantes y cabe en 160 caracteres");
  CHECK(datos.size() == 192 * 4, "trae las 192 muestras");
  int8_t ultimaT = (int8_t)datos[191 * 4];
  CHECK(ultimaT == (int8_t)lround(simTemp * 2) && datos[191 * 4 + 1] == (uint8_t)lround(simHum), "la muestra más reciente coincide con la lectura (medios grados y %)");
  CHECK(datos[191 * 4 + 2] == 30, "guarda la temperatura del lado caliente");
  n0 = nEnviados();
  cmd("HIST 2");
  correr(10000);
  CHECK(nEnviados() - n0 == 1 && contiene(ultimo().texto, "CF2;H;1/1;I15;"), "HIST 2: una sola página");

  puts("11) Módulo GSM que deja de responder");
  modem().mudo = true;
  n0 = nEnviados();
  cmd("INFO");
  modem().recibirSms(AUT, "CF2 4321 INFO");  // no llega: el módulo está mudo
  correr(3 * 60000);
  CHECK(lcd().filas[0].rfind("T 3.5", 0) == 0, "el control y la pantalla siguen funcionando");
  modem().mudo = false;
  modem().recibirSms(AUT, "CF2 4321 INFO");
  correr(2 * 60000);
  CHECK(nEnviados() > n0 && contiene(ultimo().texto, "CF2;R"), "al volver el módulo, se reinicializa y responde");

  puts("12) Estado");
  cmd("INFO");
  const std::string &r = ultimo().texto;
  CHECK(campo(r, 'T') == "3.5" && campo(r, 'S') == "3.5" && campo(r, 'E') == "1" && campo(r, 'Q') == "21" && std::fabs(std::stof(campo(r, 'C')) - 30.0f) <= 0.2f,
        "R: temperatura, objetivo, encendido, señal y lado caliente");
  CHECK(std::stoi(campo(r, 'U')) > 60 * 50, "R: minutos desde el encendido");
  printf("     %s\n", r.c_str());
  printf("     escrituras en la EEPROM: %u\n", EEPROM.escrituras);

  FILE *f = fopen(rutaEeprom, "wb");
  fwrite(EEPROM.memoria, 1, sizeof EEPROM.memoria, f);
  fclose(f);
}

void fase2(const char *rutaEeprom) {
  puts("13) Tras un corte de luz (proceso nuevo con la misma EEPROM)");
  FILE *f = fopen(rutaEeprom, "rb");
  fread(EEPROM.memoria, 1, sizeof EEPROM.memoria, f);
  fclose(f);
  ladoCaliente(30);
  setup();
  correr(5000);
  CHECK(ajustes.en == 1 && ajustes.sp == 35 && ajustes.th == 70 && ajustes.ad == 10 && ajustes.ra == 1, "conserva la configuración");
  CHECK(historialCantidad() == historialCapacidad(), "conserva el historial");
  CHECK(!modem().enviados.empty() && modem().enviados[0].texto == "CF2;E;INICIO;LUZ", "avisa que volvió la luz");
  cmd("HIST 1");
  correr(10000);
  auto b = base64url(ultimo().texto.substr(ultimo().texto.rfind(';') + 1));
  bool marca = false;
  for (size_t i = 0; i + 3 < b.size(); i += 4) marca |= b[i] == 0x80 && b[i + 1] == 254;
  CHECK(marca, "marca el reinicio en el historial");
}

int main(int argc, char **argv) {
  if (argc > 2 && std::string(argv[1]) == "--tras-corte") {
    fase2(argv[2]);
  } else {
    std::string ruta = std::string("/tmp/eeprom_cuarto_frio_") + std::to_string(MODO_SALIDA) + ".bin";
    fase1(ruta.c_str());
    printf("\n%d/%d comprobaciones correctas en la fase 1\n", total - fallos, total);
    int r = system((std::string(argv[0]) + " --tras-corte " + ruta).c_str());
    if (r != 0) fallos++;
  }
  if (argc > 2) printf("\n%d/%d comprobaciones correctas en la fase 2\n", total - fallos, total);
  return fallos != 0;
}
