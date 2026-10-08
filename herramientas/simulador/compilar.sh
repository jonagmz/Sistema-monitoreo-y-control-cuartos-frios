#!/bin/sh
# Compila el equipo virtual (firmware real + hardware simulado + modelo térmico).
cd "$(dirname "$0")" || exit 1
F=../../firmware
CXX=${CXX:-$(command -v clang++ || command -v g++)}
"$CXX" -std=c++17 -O1 -w -I $F/pruebas/mock -DPRUEBAS "-DCONFIG_PRUEBAS=\"$PWD/config_simulador.h\"" \
  $F/CuartoFrio/Ajustes.cpp $F/CuartoFrio/Alarmas.cpp $F/CuartoFrio/Control.cpp $F/CuartoFrio/Gsm.cpp $F/CuartoFrio/Hal.cpp \
  $F/CuartoFrio/Historial.cpp $F/CuartoFrio/Mensajes.cpp $F/CuartoFrio/Pantalla.cpp $F/CuartoFrio/Sensores.cpp \
  -x c++ equipo_virtual.cpp -o equipo_virtual && echo "listo: ./equipo_virtual o python3 puente.py"
