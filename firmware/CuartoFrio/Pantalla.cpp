#include "Pantalla.h"
#include "Ajustes.h"
#include "Configuracion.h"
#include "Estado.h"
#include <LiquidCrystal_I2C.h>

static LiquidCrystal_I2C lcd(0x27, 20, 4);  // SDA = A4, SCL = A5
static const uint32_t PERIODO = 1000;
static uint32_t ultima = 0;
static uint8_t alarmaMostrada = 0;

static const char AL_0[] PROGMEM = "ALARMA TEMP. ALTA";
static const char AL_1[] PROGMEM = "ALARMA TEMP. BAJA";
static const char AL_2[] PROGMEM = "ALARMA HUMEDAD ALTA";
static const char AL_3[] PROGMEM = "ALARMA HUMEDAD BAJA";
static const char AL_4[] PROGMEM = "FALLA SENSOR AMB.";
static const char AL_5[] PROGMEM = "DISIPADOR CALIENTE!";
static const char AL_6[] PROGMEM = "FALLA SENSOR DISIP.";
static const char AL_7[] PROGMEM = "FALLA ENFRIAMIENTO";
static const char *const NOMBRES_ALARMA[] PROGMEM = {AL_0, AL_1, AL_2, AL_3, AL_4, AL_5, AL_6, AL_7};

static void renglon(uint8_t fila, const char *texto) {
  char r[21];
  snprintf(r, sizeof(r), "%-20s", texto);
  lcd.setCursor(0, fila);
  lcd.print(r);
}

// "4.5" o "--" (sin decimales si se pide entero)
static void valor(char *destino, size_t tamano, int16_t decimas, bool entero) {
  if (decimas == SIN_DATO) {
    strcpy(destino, "--");
  } else if (entero) {
    snprintf(destino, tamano, "%d", (decimas + (decimas >= 0 ? 5 : -5)) / 10);
  } else {
    escribirDecimas(destino, tamano, decimas);
  }
}

void pantallaIniciar() {
  lcd.init();
  lcd.backlight();
  renglon(0, "Control cuarto frio");
  renglon(1, NOMBRE_EQUIPO);
  renglon(3, "Iniciando...");
}

void pantallaActualizar(uint32_t ahora) {
  if (ahora - ultima < PERIODO) return;
  ultima = ahora;
  char t[24], a[8], b[8];

  if (estado.fallaAmbiente) {
    renglon(0, "SIN SENSOR AMBIENTE");
  } else {
    valor(a, sizeof(a), estado.temp, false);
    valor(b, sizeof(b), estado.hum, true);
    snprintf(t, sizeof(t), "T %s\xDF" "C  H %s%%", a, b);
    renglon(0, t);
  }

  valor(a, sizeof(a), ajustes.sp, false);
#if MODO_SALIDA == SALIDA_RELES
  snprintf(t, sizeof(t), "Obj %s\xDF" " Celdas %u/4", a, estado.potencia / 25);
#else
  snprintf(t, sizeof(t), "Obj %s\xDF" " Pot %u%%", a, estado.potencia);
#endif
  renglon(1, t);

#if USAR_SENSOR_CALIENTE
  valor(a, sizeof(a), estado.caliente, true);
  snprintf(t, sizeof(t), "Disip %s\xDF" "C GSM %u", a, estado.senal);
#else
  snprintf(t, sizeof(t), "Senal GSM %u", estado.senal);
#endif
  renglon(2, t);

  if (estado.bloqueoTermico) {
    renglon(3, "BLOQUEO TERMICO");
  } else if (estado.alarmas) {
    // Si hay varias alarmas se van turnando cada segundo.
    for (uint8_t i = 0; i < 8; i++) {
      alarmaMostrada = (alarmaMostrada + 1) % 8;
      if (estado.alarmas & (1 << alarmaMostrada)) break;
    }
    strcpy_P(t, (const char *)pgm_read_ptr(&NOMBRES_ALARMA[alarmaMostrada]));
    renglon(3, t);
  } else if (!ajustes.en) {
    renglon(3, "Control APAGADO");
  } else {
    renglon(3, estado.potencia ? "Enfriando" : "En espera");
  }
}
