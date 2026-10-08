#!/bin/sh
# Compila el firmware para la computadora (con el hardware simulado) y ejecuta las pruebas en los dos modos de salida.
cd "$(dirname "$0")" || exit 1
CXX=${CXX:-$(command -v clang++ || command -v g++)}
FUENTES="../CuartoFrio/Ajustes.cpp ../CuartoFrio/Alarmas.cpp ../CuartoFrio/Control.cpp ../CuartoFrio/Gsm.cpp ../CuartoFrio/Hal.cpp ../CuartoFrio/Historial.cpp ../CuartoFrio/Mensajes.cpp ../CuartoFrio/Pantalla.cpp ../CuartoFrio/Sensores.cpp"
resultado=0
for modo in reles pwm; do
  "$CXX" -std=c++17 -O1 -w -I mock -DPRUEBAS "-DCONFIG_PRUEBAS=\"$PWD/config_$modo.h\"" $FUENTES -x c++ pruebas.cpp -o "/tmp/pruebas_cuarto_frio_$modo" || exit 1
  "/tmp/pruebas_cuarto_frio_$modo" || resultado=1
done
exit $resultado
