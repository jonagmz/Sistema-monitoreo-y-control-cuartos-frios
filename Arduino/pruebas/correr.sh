#!/bin/sh
# Compila y ejecuta la simulación del firmware (necesita clang++ o g++).
cd "$(dirname "$0")" || exit 1
CXX=${CXX:-$(command -v clang++ || command -v g++)}
"$CXX" -std=c++17 -w -I mock -x c++ simulacion.cpp -o /tmp/simulacion_cuarto_frio && /tmp/simulacion_cuarto_frio
