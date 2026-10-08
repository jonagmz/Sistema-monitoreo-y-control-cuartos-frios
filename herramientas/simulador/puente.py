#!/usr/bin/env python3
"""Conecta el equipo virtual (firmware real) con la app en un emulador Android mediante SMS simulados.

- Los SMS que la app envía al número del equipo entran al firmware como si vinieran de +520000000001.
- Los SMS que envía el firmware llegan a la app desde el número del equipo (adb emu sms send).

Uso: ANDROID_HOME=... python3 puente.py
Órdenes en control.txt (una por línea, se leen en vivo):
  VELOCIDAD <x>   multiplica el tiempo (x60 = 1 hora por minuto)
  AMBIENTE <°C> | PUERTA <s> | VENTILADOR <0|1> | SENSOR <0|1>
  INTRUSO <texto> SMS desde otro número (la app debe ignorarlo)
"""
import os
import subprocess
import time

AQUI = os.path.dirname(os.path.abspath(__file__))
ADB = os.path.join(os.environ["ANDROID_HOME"], "platform-tools", "adb")
NUMERO_EQUIPO = "6670000001"           # el que se escribe en la app
NUMERO_TELEFONO = "+520000000001"      # NUMEROS_AUTORIZADOS de config_simulador.h
CONTROL = os.path.join(AQUI, "control.txt")
TICK = 0.5


def log(msg):
    print(time.strftime("%H:%M:%S ") + msg, flush=True)


def adb(*args):
    return subprocess.run([ADB, *args], capture_output=True, text=True).stdout


def enviados():
    """SMS enviados por la app (el sistema los guarda en el proveedor de SMS)."""
    filas = []
    for linea in adb("shell", "content", "query", "--uri", "content://sms/sent", "--projection", "_id:address:body").splitlines():
        if linea.startswith("Row:"):
            campos = dict(p.split("=", 1) for p in linea.split(" ", 2)[2].split(", ", 2))
            filas.append((int(campos["_id"]), campos["address"], campos["body"]))
    return sorted(filas)  # el proveedor los devuelve del más nuevo al más viejo


binario = os.path.join(AQUI, "equipo_virtual")
equipo = subprocess.Popen([binario], stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True, encoding="latin-1", bufsize=1)


def orden(texto):
    equipo.stdin.write(texto + "\n")
    equipo.stdin.flush()


def avanzar(ms):
    orden(f"RUN {ms}")
    while True:
        linea = equipo.stdout.readline().rstrip("\n")
        if linea.startswith("SMS_OUT"):
            _, _, texto = linea.split("\t", 2)
            log(f"EQUIPO -> APP  {texto}")
            adb("emu", "sms", "send", NUMERO_EQUIPO, texto)
        elif linea.startswith("ESTADO"):
            return linea.split("\t")


if not os.path.exists(CONTROL):
    open(CONTROL, "w").close()
leidas = len(open(CONTROL).read().splitlines())
ultimo_id = max((f[0] for f in enviados()), default=0)
velocidad, vueltas = 1.0, 0
log(f"Equipo virtual listo. En la app usa el número {NUMERO_EQUIPO} y el PIN 4321.")

while True:
    lineas = open(CONTROL).read().splitlines()
    for c in lineas[leidas:]:
        clave, _, valor = c.partition(" ")
        if clave == "VELOCIDAD":
            velocidad = float(valor)
            log(f"velocidad x{velocidad:g}")
        elif clave == "INTRUSO":
            log(f"INTRUSO -> APP  {valor}")
            adb("emu", "sms", "send", "5559998888", valor)
        else:
            orden(c)
            log(f"control: {c}")
    leidas = len(lineas)

    for _id, numero, texto in enviados():
        if _id > ultimo_id:
            ultimo_id = _id
            log(f"APP -> EQUIPO  {texto}")
            if numero.replace(" ", "").endswith(NUMERO_EQUIPO):
                orden(f"SMS {NUMERO_TELEFONO} {texto}")

    e = avanzar(int(TICK * 1000 * velocidad))
    vueltas += 1
    if vueltas % 20 == 0:
        log(f"t={int(e[1]) // 60} min  cuarto {float(e[2]):.1f} °C  disipador {float(e[4]):.0f} °C  potencia {e[5]} %  [{e[6].strip()}]")
    time.sleep(TICK)
