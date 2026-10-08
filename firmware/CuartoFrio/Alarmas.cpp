#include "Alarmas.h"
#include "Ajustes.h"
#include "Configuracion.h"
#include "Estado.h"

static const uint32_t MINIMO_ENTRE_AVISOS = 2UL * 60000UL;

// Desde cuándo se cumple cada condición con retardo; el bit i de "cumpliendo" indica si se cumple.
static uint32_t desde[4];
static uint8_t cumpliendo = 0;
static const uint8_t CON_RETARDO[4] = {AL_TEMP_ALTA, AL_TEMP_BAJA, AL_HUM_ALTA, AL_HUM_BAJA};

static uint8_t notificadas = 0, reconocidas = 0;
static uint32_t ultimoAviso = 0;
static bool hayAvisoPrevio = false;

void alarmasActualizar(uint32_t ahora) {
  bool condicion[4] = {false, false, false, false};
  if (ajustes.en && !estado.fallaAmbiente && estado.temp != SIN_DATO) {
    condicion[0] = estado.temp > ajustes.th;
    condicion[1] = estado.temp < ajustes.tl;
    condicion[2] = estado.hum > ajustes.hh * 10;
    condicion[3] = estado.hum < ajustes.hl * 10;
  }
  uint8_t activas = 0;
  uint32_t retardo = (uint32_t)ajustes.ad * 60000UL;
  for (uint8_t i = 0; i < 4; i++) {
    uint8_t bit = 1 << i;
    if (!condicion[i]) {
      cumpliendo &= ~bit;
      continue;
    }
    if (!(cumpliendo & bit)) {
      cumpliendo |= bit;
      desde[i] = ahora;
    }
    if (ahora - desde[i] >= retardo) activas |= CON_RETARDO[i];
  }
  // Las fallas de equipo avisan al momento.
  if (estado.fallaAmbiente) activas |= AL_SENSOR;
  if (estado.protegiendo) activas |= AL_SOBRECALENTADO;
#if USAR_SENSOR_CALIENTE
  if (estado.fallaCaliente) activas |= AL_SENSOR_CALIENTE;
#endif
  if (estado.fallaEnfriamiento) activas |= AL_FALLA_ENFRIAMIENTO;

  estado.alarmas = activas;
  reconocidas &= activas;  // una alarma que se resolvió y vuelve cuenta como nueva
}

bool alarmasHayQueAvisar(uint32_t ahora) {
  bool espera = hayAvisoPrevio && ahora - ultimoAviso < MINIMO_ENTRE_AVISOS;
  if (estado.alarmas != notificadas) return !espera;
  uint8_t pendientes = estado.alarmas & ~reconocidas;
  return pendientes && ajustes.ra && ahora - ultimoAviso >= (uint32_t)ajustes.ra * 3600000UL;
}

void alarmasAvisoEnviado(uint32_t ahora) {
  notificadas = estado.alarmas;
  ultimoAviso = ahora;
  hayAvisoPrevio = true;
}

void alarmasReconocer() {
  reconocidas = estado.alarmas;
}
