/*
  Sistema de monitoreo y control de cuarto frío.

  Hardware: Arduino Uno, módulo GSM SIM800L (SoftwareSerial en pines 7 y 8), LCD I2C 20x4 (0x27),
  sensor DHT en el pin 2 y 4 relés activos en LOW en los pines 3 a 6.

  Protocolo SMS
  - Configuración (desde la app): "@*tempMin*tempMax*humMin*humMax*sistema"   ej. "@*2*6*80*95*1"
  - Pedir lecturas al momento:    "INFO"
  - Reporte que envía el equipo:
      temp,hum,ubicacion,alarmaTempBaja,alarmaTempAlta,alarmaHumBaja,alarmaHumAlta,
      errorSensor,enfriando,sistema,tempMin,tempMax,humMin,humMax
    Los 7 primeros campos son los del formato original, así que la app anterior sigue funcionando.
*/
#include <avr/wdt.h>
#include <EEPROM.h>
#include <SoftwareSerial.h>
#include <LiquidCrystal_I2C.h>
#include "DHT.h"

#if !defined(PRUEBAS) && __has_include("config.h")
#include "config.h"
#else
#warning "No existe config.h: se usa config.example.h. Copialo como config.h y pon tus numeros."
#include "config.example.h"
#endif

const uint8_t PIN_GSM_RX = 7;
const uint8_t PIN_GSM_TX = 8;
const uint8_t PIN_DHT = 2;
const uint8_t PINES_RELE[] = {3, 4, 5, 6};
const uint8_t RELE_ENCENDIDO = LOW;   // módulos de relé activos en LOW
const uint8_t RELE_APAGADO = HIGH;

const unsigned long INTERVALO_REPORTE = 15UL * 60UL * 1000UL;
const unsigned long INTERVALO_LECTURA = 2000UL;           // el DHT no da lecturas nuevas más rápido
const unsigned long INTERVALO_LCD = 1000UL;
const unsigned long ESPERA_ENTRE_ALARMAS = 5UL * 60UL * 1000UL;
const unsigned long TIEMPO_MIN_APAGADO = 3UL * 60UL * 1000UL;  // evita arranques seguidos del compresor
const uint8_t LECTURAS_FALLIDAS_PARA_ERROR = 3;

const int8_t TEMP_LIMITE_MIN = -30, TEMP_LIMITE_MAX = 50;

// Bits de la máscara de alarmas
const uint8_t ALARMA_TEMP_BAJA = 1, ALARMA_TEMP_ALTA = 2, ALARMA_HUM_BAJA = 4, ALARMA_HUM_ALTA = 8,
              ALARMA_SENSOR = 16;

struct Config {
  uint16_t firma;
  int8_t tempMin, tempMax;
  uint8_t humMin, humMax;
  bool sistemaEncendido;
};
const uint16_t FIRMA_CONFIG = 0xCF01;
const int DIRECCION_CONFIG = 0;

SoftwareSerial gsm(PIN_GSM_RX, PIN_GSM_TX);
LiquidCrystal_I2C lcd(0x27, 20, 4);  // SCL = A5, SDA = A4
DHT dht(PIN_DHT, TIPO_SENSOR);

Config config;
float temperatura = NAN, humedad = NAN;
uint8_t lecturasFallidas = 0;
bool enfriando = false;
uint8_t alarmas = 0, alarmasNotificadas = 0;
unsigned long ultimoReporte = 0, ultimaLectura = 0, ultimoLcd = 0, ultimaAlarmaEnviada = 0, ultimoApagado = 0;
bool yaSeEnvioAlarma = false;

char lineaGsm[161];
uint8_t largoLinea = 0;
bool esperandoCuerpoSms = false;
char remitente[24];

// ---------------------------------------------------------------- configuración persistente

bool configValida(const Config &c) {
  return c.tempMin >= TEMP_LIMITE_MIN && c.tempMax <= TEMP_LIMITE_MAX && c.tempMin < c.tempMax &&
         c.humMax <= 100 && c.humMin < c.humMax;
}

void cargarConfig() {
  EEPROM.get(DIRECCION_CONFIG, config);
  if (config.firma != FIRMA_CONFIG || !configValida(config)) {
    config = {FIRMA_CONFIG, 2, 6, 80, 95, false};  // el sistema arranca apagado hasta que se configure
    EEPROM.put(DIRECCION_CONFIG, config);
  }
}

// ---------------------------------------------------------------- relés y control

void ponerReles(bool encender) {
  for (uint8_t pin : PINES_RELE) {
    digitalWrite(pin, encender ? RELE_ENCENDIDO : RELE_APAGADO);
  }
  if (enfriando && !encender) {
    ultimoApagado = millis();
  }
  enfriando = encender;
}

void controlarTemperatura() {
  if (!config.sistemaEncendido) {
    if (enfriando) ponerReles(false);
    return;
  }
  if (alarmas & ALARMA_SENSOR) {
    return;  // sin lectura fiable se mantiene el estado actual y se avisa por SMS
  }
  if (!enfriando && temperatura >= config.tempMax) {
    if (millis() - ultimoApagado >= TIEMPO_MIN_APAGADO) ponerReles(true);
  } else if (enfriando && temperatura <= config.tempMin) {
    ponerReles(false);
  }
}

void calcularAlarmas() {
  uint8_t nuevas = 0;
  if (lecturasFallidas >= LECTURAS_FALLIDAS_PARA_ERROR) {
    nuevas |= ALARMA_SENSOR;
  } else if (config.sistemaEncendido && !isnan(temperatura)) {
    if (temperatura < config.tempMin) nuevas |= ALARMA_TEMP_BAJA;
    if (temperatura > config.tempMax) nuevas |= ALARMA_TEMP_ALTA;
    if (humedad < config.humMin) nuevas |= ALARMA_HUM_BAJA;
    if (humedad > config.humMax) nuevas |= ALARMA_HUM_ALTA;
  }
  alarmas = nuevas;
}

void leerSensor() {
  float t = dht.readTemperature();
  float h = dht.readHumidity();
  if (isnan(t) || isnan(h)) {
    if (lecturasFallidas < 255) lecturasFallidas++;
    return;
  }
  lecturasFallidas = 0;
  temperatura = t;
  humedad = h;
}

// ---------------------------------------------------------------- SMS

bool esperarCaracter(char esperado, unsigned long tiempoMax) {
  unsigned long inicio = millis();
  while (millis() - inicio < tiempoMax) {
    wdt_reset();
    if (gsm.available() && gsm.read() == esperado) return true;
  }
  return false;
}

void enviarSms(const char *numero, const char *texto) {
  if (MODO_DEPURACION) {
    Serial.print(F("SMS a "));
    Serial.print(numero);
    Serial.print(F(": "));
    Serial.println(texto);
  }
  gsm.print(F("AT+CMGS=\""));
  gsm.print(numero);
  gsm.println('"');
  if (!esperarCaracter('>', 5000)) {
    gsm.write(27);  // ESC cancela el envío si el módulo no respondió
    return;
  }
  gsm.print(texto);
  gsm.write(26);  // Ctrl+Z envía el mensaje
  delay(100);
}

void formatearNumero(char *destino, float valor) {
  if (isnan(valor)) {
    strcpy(destino, "ND");
  } else {
    dtostrf(valor, 0, 1, destino);
  }
}

void enviarReporte(const char *numero) {
  char temp[8], hum[8], texto[120];
  bool sinLectura = alarmas & ALARMA_SENSOR;
  formatearNumero(temp, sinLectura ? NAN : temperatura);
  formatearNumero(hum, sinLectura ? NAN : humedad);
  snprintf(texto, sizeof(texto), "%s,%s,%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d", temp, hum, UBICACION,
           (alarmas & ALARMA_TEMP_BAJA) != 0, (alarmas & ALARMA_TEMP_ALTA) != 0,
           (alarmas & ALARMA_HUM_BAJA) != 0, (alarmas & ALARMA_HUM_ALTA) != 0,
           (alarmas & ALARMA_SENSOR) != 0, enfriando, config.sistemaEncendido, config.tempMin,
           config.tempMax, config.humMin, config.humMax);
  enviarSms(numero, texto);
  ultimoReporte = millis();
}

bool ultimosDiezDigitosIguales(const char *a, const char *b) {
  size_t la = strlen(a), lb = strlen(b);
  if (la < 10 || lb < 10) return false;
  return strcmp(a + la - 10, b + lb - 10) == 0;
}

bool numeroAutorizado(const char *numero) {
  for (const char *autorizado : NUMEROS_AUTORIZADOS) {
    if (ultimosDiezDigitosIguales(numero, autorizado)) return true;
  }
  return false;
}

// Lee "@*tempMin*tempMax*humMin*humMax*sistema". Devuelve false si falta algún valor o no es válido.
bool leerComandoConfig(const char *texto, Config &nueva) {
  const char *p = strchr(texto, '@');
  long valores[5];
  for (uint8_t i = 0; i < 5; i++) {
    p = p ? strchr(p, '*') : NULL;
    if (!p) return false;
    char *fin;
    valores[i] = strtol(++p, &fin, 10);
    if (fin == p) return false;
    p = fin;
  }
  if (valores[0] < TEMP_LIMITE_MIN || valores[1] > TEMP_LIMITE_MAX || valores[2] < 0 || valores[3] > 100 ||
      (valores[4] != 0 && valores[4] != 1)) {
    return false;
  }
  nueva = {FIRMA_CONFIG, (int8_t)valores[0], (int8_t)valores[1], (uint8_t)valores[2], (uint8_t)valores[3],
           valores[4] == 1};
  return configValida(nueva);
}

void procesarSms(const char *numero, const char *texto) {
  if (!numeroAutorizado(numero)) {
    if (MODO_DEPURACION) {
      Serial.print(F("SMS ignorado de numero no autorizado: "));
      Serial.println(numero);
    }
    return;
  }
  if (strchr(texto, '@')) {
    Config nueva;
    if (leerComandoConfig(texto, nueva)) {
      config = nueva;
      EEPROM.put(DIRECCION_CONFIG, config);
      calcularAlarmas();
      controlarTemperatura();
      enviarReporte(numero);  // confirma con los valores ya aplicados
      alarmasNotificadas = alarmas;  // el reporte ya incluye el estado de las alarmas
    } else {
      enviarSms(numero, "ERROR: configuracion invalida. Formato @*tMin*tMax*hMin*hMax*0|1");
    }
  } else if (strncasecmp(texto, "INFO", 4) == 0) {
    enviarReporte(numero);
  }
}

// Las notificaciones de SMS llegan como dos líneas: '+CMT: "<numero>",...' y luego el texto.
void procesarLineaGsm(const char *linea) {
  if (MODO_DEPURACION) Serial.println(linea);
  if (esperandoCuerpoSms) {
    esperandoCuerpoSms = false;
    procesarSms(remitente, linea);
    return;
  }
  if (strncmp(linea, "+CMT:", 5) == 0) {
    const char *inicio = strchr(linea, '"');
    const char *fin = inicio ? strchr(inicio + 1, '"') : NULL;
    if (inicio && fin && (size_t)(fin - inicio - 1) < sizeof(remitente)) {
      memcpy(remitente, inicio + 1, fin - inicio - 1);
      remitente[fin - inicio - 1] = '\0';
      esperandoCuerpoSms = true;
    }
  }
}

void leerGsm() {
  while (gsm.available()) {
    char c = gsm.read();
    if (c == '\r') continue;
    if (c == '\n') {
      lineaGsm[largoLinea] = '\0';
      if (largoLinea > 0) procesarLineaGsm(lineaGsm);
      largoLinea = 0;
    } else if (largoLinea < sizeof(lineaGsm) - 1) {
      lineaGsm[largoLinea++] = c;
    }
  }
}

void comandoAt(const __FlashStringHelper *comando) {
  gsm.println(comando);
  delay(500);
  while (gsm.available()) gsm.read();
}

// ---------------------------------------------------------------- pantalla

void lineaLcd(uint8_t fila, const char *texto) {
  char renglon[21];
  snprintf(renglon, sizeof(renglon), "%-20s", texto);
  lcd.setCursor(0, fila);
  lcd.print(renglon);
}

void actualizarLcd() {
  char texto[24], temp[8], hum[8];
  formatearNumero(temp, temperatura);
  formatearNumero(hum, humedad);
  if (alarmas & ALARMA_SENSOR) {
    lineaLcd(0, "ERROR DE SENSOR");
  } else {
    snprintf(texto, sizeof(texto), "T %s\xDF" "C  H %s%%", temp, hum);
    lineaLcd(0, texto);
  }
  snprintf(texto, sizeof(texto), "SP T %d a %d\xDF" "C", config.tempMin, config.tempMax);
  lineaLcd(1, texto);
  snprintf(texto, sizeof(texto), "SP H %d a %d%%", config.humMin, config.humMax);
  lineaLcd(2, texto);

  if (alarmas & (ALARMA_TEMP_BAJA | ALARMA_TEMP_ALTA | ALARMA_HUM_BAJA | ALARMA_HUM_ALTA)) {
    snprintf(texto, sizeof(texto), "ALARMA%s%s%s%s", (alarmas & ALARMA_TEMP_BAJA) ? " T-" : "",
             (alarmas & ALARMA_TEMP_ALTA) ? " T+" : "", (alarmas & ALARMA_HUM_BAJA) ? " H-" : "",
             (alarmas & ALARMA_HUM_ALTA) ? " H+" : "");
    lineaLcd(3, texto);
  } else if (!config.sistemaEncendido) {
    lineaLcd(3, "Sistema APAGADO");
  } else {
    lineaLcd(3, enfriando ? "Enfriando" : "En reposo");
  }
}

// ---------------------------------------------------------------- programa principal

void setup() {
  for (uint8_t pin : PINES_RELE) {
    digitalWrite(pin, RELE_APAGADO);  // antes de pinMode para que el relé no se active al arrancar
    pinMode(pin, OUTPUT);
  }
  Serial.begin(115200);
  gsm.begin(9600);
  dht.begin();
  lcd.init();
  lcd.backlight();
  cargarConfig();

  lineaLcd(0, "Sistema de monitoreo");
  lineaLcd(1, "y control de un");
  lineaLcd(2, "cuarto frio");
  delay(3000);
  lcd.clear();
  lineaLcd(0, "Configurando GSM...");
  delay(2000);  // el SIM800L necesita unos segundos tras encenderse
  comandoAt(F("AT"));
  comandoAt(F("AT+CMGF=1"));          // SMS en modo texto
  comandoAt(F("AT+CNMI=2,2,0,0,0"));  // reenvía los SMS recibidos por el puerto serie sin guardarlos
  lcd.clear();

  leerSensor();
  ultimaLectura = millis();
  ultimoApagado = millis();  // tras un corte de luz el compresor también espera el tiempo mínimo
  ultimoReporte = millis();
  wdt_enable(WDTO_8S);  // si el programa se cuelga (I2C, GSM...), el Arduino se reinicia solo
}

void loop() {
  wdt_reset();
  leerGsm();

#if MODO_DEPURACION
  while (Serial.available()) gsm.write(Serial.read());  // comandos AT manuales desde el monitor serie
#endif

  // Leer el DHT desactiva interrupciones unos milisegundos y SoftwareSerial perdería bytes,
  // así que no se lee mientras está llegando una línea del módulo GSM.
  if (millis() - ultimaLectura >= INTERVALO_LECTURA && largoLinea == 0 && !esperandoCuerpoSms &&
      !gsm.available()) {
    ultimaLectura = millis();
    leerSensor();
    calcularAlarmas();
    controlarTemperatura();
  }

  if (millis() - ultimoLcd >= INTERVALO_LCD) {
    ultimoLcd = millis();
    actualizarLcd();
  }

  // Avisa en cuanto aparece o se resuelve una alarma, sin mandar más de un SMS cada 5 minutos.
  if (alarmas != alarmasNotificadas &&
      (!yaSeEnvioAlarma || millis() - ultimaAlarmaEnviada >= ESPERA_ENTRE_ALARMAS)) {
    enviarReporte(NUMERO_REPORTES);
    alarmasNotificadas = alarmas;
    ultimaAlarmaEnviada = millis();
    yaSeEnvioAlarma = true;
  } else if (millis() - ultimoReporte >= INTERVALO_REPORTE) {
    enviarReporte(NUMERO_REPORTES);
  }
}
