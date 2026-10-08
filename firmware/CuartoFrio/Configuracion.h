// Carga config.h (o el ejemplo) y define las constantes que dependen de él.
#pragma once

#define SALIDA_RELES 1
#define SALIDA_PWM 2

#if defined(CONFIG_PRUEBAS)
#include CONFIG_PRUEBAS  // las pruebas en la computadora usan su propia configuración
#elif !defined(PRUEBAS) && __has_include("config.h")
#include "config.h"
#else
#include "config.example.h"
#endif
