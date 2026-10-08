#include "Control.h"
#include "Ajustes.h"
#include "Configuracion.h"
#include "Estado.h"
#include "Hal.h"

// Relés: como mucho un cambio de etapa por minuto, así cada celda queda al menos 1 min encendida o apagada.
static const uint32_t TIEMPO_ENTRE_ETAPAS = 60000;
static const uint8_t HISTERESIS_ETAPA = 5;   // % de margen para no oscilar entre dos etapas
static const uint8_t RAMPA_PWM = 10;         // % por ciclo de control (2 %/s): cambios suaves para la celda
static const uint8_t LIMITE_SIN_SENSOR = 50; // % si falla el sensor del lado caliente

// Falla de enfriamiento: potencia alta 60 min sin bajar 0.5 °C estando 2 °C por encima del objetivo.
static const uint32_t VENTANA_FALLA = 60UL * 60000UL;

static float integral = 0;
static int16_t tempAnterior = SIN_DATO;
static uint32_t ultimoControl = 0;

static uint8_t etapas = 0, primeraCelda = 0;
static uint32_t ultimoCambioEtapa = 0;

// Bloqueo térmico: si la protección salta DISPAROS_PARA_BLOQUEO veces en VENTANA_BLOQUEO, casi seguro falló el
// ventilador del disipador. Rearmar una y otra vez solo estresa las celdas y manda un SMS tras otro.
static const uint8_t DISPAROS_PARA_BLOQUEO = 3;
static const uint32_t VENTANA_BLOQUEO = 60UL * 60000UL;
static uint32_t disparos[DISPAROS_PARA_BLOQUEO];
static uint8_t numDisparos = 0;

static uint32_t inicioVentanaFalla = 0;
static int16_t tempInicioVentana = SIN_DATO;

void controlIniciar(uint32_t ahora) {
  ultimoControl = ahora;
  ultimoCambioEtapa = ahora - TIEMPO_ENTRE_ETAPAS;
}

void controlDesbloquear() {
  estado.bloqueoTermico = false;
  numDisparos = 0;
}

static void registrarDisparo(uint32_t ahora) {
  estado.ultimoDisparo = ahora;
  // Se guardan los últimos disparos; si el más antiguo de los 3 fue hace menos de una hora, se bloquea.
  if (numDisparos < DISPAROS_PARA_BLOQUEO) {
    disparos[numDisparos++] = ahora;
  } else {
    for (uint8_t i = 1; i < DISPAROS_PARA_BLOQUEO; i++) disparos[i - 1] = disparos[i];
    disparos[DISPAROS_PARA_BLOQUEO - 1] = ahora;
  }
  if (numDisparos == DISPAROS_PARA_BLOQUEO && ahora - disparos[0] < VENTANA_BLOQUEO) estado.bloqueoTermico = true;
}

uint8_t limitePorLadoCaliente(int16_t caliente, bool fallaSensor, uint8_t maximoC, uint32_t ahora) {
#if !USAR_SENSOR_CALIENTE
  (void)caliente; (void)fallaSensor; (void)maximoC; (void)ahora;
  return 100;
#else
  if (estado.bloqueoTermico) return 0;
  if (fallaSensor || caliente == SIN_DATO) {
    estado.protegiendo = false;
    return LIMITE_SIN_SENSOR;
  }
  int16_t maximo = (int16_t)maximoC * 10;
  if (estado.protegiendo) {
    if (caliente > maximo - 150) return 0;  // se rearma cuando bajó 15 °C
    estado.protegiendo = false;
  }
  if (caliente >= maximo) {
    estado.protegiendo = true;
    registrarDisparo(ahora);
    return 0;
  }
  if (caliente > maximo - 100) return (uint8_t)(maximo - caliente);  // de 100 % a 0 % en los últimos 10 °C (100 décimas)
  return 100;
#endif
}

// PID con la derivada sobre la medición (no salta al cambiar el objetivo) y anti-windup por saturación.
static uint8_t calcularPid(float dtMin) {
  float error = (estado.temp - ajustes.sp) / 10.0f;  // positivo = hace falta enfriar
  float derivada = (tempAnterior == SIN_DATO || dtMin <= 0) ? 0 : (estado.temp - tempAnterior) / 10.0f / dtMin;
  tempAnterior = estado.temp;

  float kp = ajustes.kp / 10.0f, ki = ajustes.ki / 10.0f, kd = ajustes.kd / 10.0f;
  float sinIntegral = kp * error + kd * derivada;
  float candidata = integral + ki * error * dtMin;
  float salida = sinIntegral + candidata;
  // Solo se integra si la salida no está saturada en la misma dirección del error.
  if (!((salida > ajustes.pm && error > 0) || (salida < 0 && error < 0))) integral = candidata;
  if (integral < 0) integral = 0;
  if (integral > ajustes.pm) integral = ajustes.pm;
  salida = sinIntegral + integral;
  if (salida < 0) salida = 0;
  if (salida > ajustes.pm) salida = ajustes.pm;
  return (uint8_t)(salida + 0.5f);
}

#if MODO_SALIDA == SALIDA_RELES
// Las celdas encendidas son primeraCelda .. primeraCelda+etapas-1 (circular). Al quitar una etapa se apaga
// la que lleva más tiempo encendida y la siguiente pasa a ser la primera: el desgaste se reparte entre las 4.
static void aplicarEtapas(uint8_t objetivo, uint32_t ahora, bool inmediato) {
  while (etapas != objetivo) {
    if (!inmediato && ahora - ultimoCambioEtapa < TIEMPO_ENTRE_ETAPAS) break;
    if (objetivo > etapas) {
      hal::rele((primeraCelda + etapas) % 4, true);
      etapas++;
    } else {
      hal::rele(primeraCelda, false);
      primeraCelda = (primeraCelda + 1) % 4;
      etapas--;
    }
    ultimoCambioEtapa = ahora;
    if (!inmediato) break;  // una etapa por vez
  }
  estado.potencia = etapas * 25;
}

static uint8_t etapasPara(uint8_t potencia) {
  // Sube de etapa al pasar el punto medio + histéresis y baja al quedar por debajo del punto medio - histéresis.
  int16_t medio = etapas * 25;
  if (potencia >= medio + 12 + HISTERESIS_ETAPA && etapas < 4) return etapas + 1;
  if (etapas > 0 && potencia + 12 + HISTERESIS_ETAPA <= medio) return etapas - 1;
  return etapas;
}
#endif

static void aplicarSalida(uint8_t potencia, uint32_t ahora, bool inmediato) {
#if MODO_SALIDA == SALIDA_RELES
  aplicarEtapas(inmediato ? (potencia + 12) / 25 : etapasPara(potencia), ahora, inmediato);
#else
  int16_t actual = estado.potencia;
  if (inmediato || potencia < actual) actual = potencia;  // bajar siempre es inmediato (protección)
  else if (potencia > actual) actual = (potencia < actual + RAMPA_PWM) ? potencia : actual + RAMPA_PWM;
  estado.potencia = (uint8_t)actual;
  hal::pwm(estado.potencia);
#endif
}

static void vigilarEnfriamiento(uint32_t ahora) {
  bool exigido = estado.potencia >= 90 && estado.temp > ajustes.sp + 20;
  if (!exigido) {
    inicioVentanaFalla = ahora;
    tempInicioVentana = estado.temp;
    if (estado.temp != SIN_DATO && estado.temp <= ajustes.sp + 10) estado.fallaEnfriamiento = false;
    return;
  }
  if (tempInicioVentana == SIN_DATO || estado.temp < tempInicioVentana - 5) {  // está bajando: todo bien
    inicioVentanaFalla = ahora;
    tempInicioVentana = estado.temp;
  } else if (ahora - inicioVentanaFalla >= VENTANA_FALLA) {
    estado.fallaEnfriamiento = true;
  }
}

void controlActualizar(uint32_t ahora) {
  float dtMin = (ahora - ultimoControl) / 60000.0f;
  ultimoControl = ahora;
  uint8_t limite = limitePorLadoCaliente(estado.caliente, estado.fallaCaliente, ajustes.pc, ahora);

  if (!ajustes.en) {
    integral = 0;
    tempAnterior = SIN_DATO;
    estado.fallaEnfriamiento = false;
    aplicarSalida(0, ahora, true);
    return;
  }
  if (estado.fallaAmbiente || estado.temp == SIN_DATO) {
    // Sin lectura no se puede regular: se mantiene la potencia actual (respetando la protección) y se avisa.
    aplicarSalida(min(estado.potencia, limite), ahora, limite < estado.potencia);
    return;
  }
  uint8_t pedida = min(calcularPid(dtMin), limite);
  aplicarSalida(pedida, ahora, limite == 0);
  vigilarEnfriamiento(ahora);
}
