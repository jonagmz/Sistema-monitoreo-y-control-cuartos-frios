# Sistema de monitoreo y control de cuarto frío · v2

Control de un cuarto frío con **celdas Peltier**, monitoreado y configurado desde el celular por **SMS** (no necesita internet). Proyecto de residencia profesional, rediseñado en 2026.

```
 Cuarto frío                                                        Celular
┌──────────────────────────────────────────────────────┐          ┌──────────────────────────┐
│ DHT (ambiente) ─┐                                    │          │ App Android (Compose)    │
│ NTC (disipador) ┼─► Arduino Uno ─► 4 relés o 2 MOSFET│   SMS    │ · Panel en vivo          │
│                 │   PID + alarmas  └► celdas Peltier │ ◄──────► │ · Historial y gráficas   │
│        LCD 20x4 ┘   historial 48 h                   │          │ · Eventos y alarmas      │
│                     SIM800L ─────────────────────────┼──────────┤ · Configuración remota   │
└──────────────────────────────────────────────────────┘          └──────────────────────────┘
```

## Qué hace

**Equipo (firmware)**
- **PID** de temperatura con anti-windup. Con los **4 relés** enciende de 0 a 4 celdas (control por etapas) y rota cuál arranca primero para repartir el desgaste. Con **MOSFET** regula la potencia por PWM a 25 kHz.
- **Protección del lado caliente**: un termistor en el disipador reduce la potencia 10 °C antes del límite y apaga las celdas al alcanzarlo.
- **Alarmas** de temperatura y humedad con retardo configurable (para no alarmar al abrir la puerta), falla de sensores, sobrecalentamiento y **falla de enfriamiento** (potencia máxima una hora sin bajar la temperatura). Avisa al momento, recuerda cada N horas y se pueden reconocer.
- **Historial de 48 h** en la EEPROM: sobrevive a cortes de luz y se descarga a la app en unos 8 SMS.
- **Aviso de corte de luz**: al volver la energía envía un SMS.
- **Seguridad**: solo obedece a números autorizados **y** con PIN.
- **Robustez**: nada bloquea el programa, el módulo GSM se reinicializa solo si deja de responder y hay watchdog.

**App**
- **Panel**: temperatura, tendencia, humedad, potencia, lado caliente, señal y alarmas con botón para reconocerlas.
- **Historial**: gráficas de temperatura, humedad y celdas (6 h a 30 días), estadísticas y exportación a CSV.
- **Eventos**: alarmas, cortes de luz, cambios de configuración y errores, agrupados por día.
- **Ajustes**: objetivo, alarmas, retardos, potencia, límite del disipador, reportes y PID. Muestra si el equipo confirmó los cambios.
- Notificaciones de alarma aunque la app esté cerrada. Tema claro y oscuro.

## Hardware

| Componente | Conexión |
|---|---|
| Arduino Uno | — |
| SIM800L | RX del Arduino → pin 7, TX → pin 8. Fuente propia de 3.7–4.2 V capaz de dar **2 A en picos** |
| DHT22 (recomendado) o DHT11 | Datos → pin 2 |
| Termistor NTC 10 kΩ (B 3950) en el disipador | Entre A0 y GND, con una resistencia de 10 kΩ de A0 a 5 V |
| LCD 20x4 I2C (0x27) | SDA → A4, SCL → A5 |
| **Modo relés**: 4 relés activos en LOW | Pines 3, 4, 5 y 6 (una celda cada uno) |
| **Modo PWM**: 2 MOSFET de nivel lógico (p. ej. IRLB8721) | Pines 9 y 10 (dos celdas cada uno), con diodo y disipador |

Recomendaciones para las Peltier:
- **Ventiladores** del lado caliente siempre encendidos mientras funcione la celda, y un **termostato o fusible térmico** en el disipador (p. ej. KSD301 de 70 °C) como protección independiente del Arduino.
- Revisa que los relés soporten la **corriente continua** de cada celda (4 a 10 A). Los MOSFET duran más y permiten regular la potencia.
- Calcula la **fuente** para todas las celdas y los ventiladores, con margen. Prevé el drenaje de la **condensación**.
- El DHT11 no mide por debajo de 0 °C: para congelación usa DHT22.

## Firmware

1. Instala desde el gestor de librerías del Arduino IDE: *DHT sensor library* y *Adafruit Unified Sensor* (Adafruit), y *LiquidCrystal I2C* (Frank de Brabander).
2. Copia `firmware/CuartoFrio/config.example.h` como `config.h` y ajusta números, PIN, sensor y modo de salida. `config.h` no se sube a git.
3. Abre `firmware/CuartoFrio/CuartoFrio.ino` y súbelo a un Arduino Uno. Ocupa 69 % de la flash y 70 % de la RAM.

Al primer arranque el control está **apagado**: se enciende desde la app.

| Módulo | Qué hace |
|---|---|
| `Hal` | Único módulo que toca pines y registros: relés, PWM a 25 kHz, ADC y causa del arranque |
| `Ajustes` | Configuración en EEPROM con CRC; una tabla define claves, rangos y formato |
| `Sensores` | DHT con mediana de 3 lecturas; NTC con promedio y detección de cable abierto o en corto |
| `Control` | PID, etapas o PWM con rampa, límite por lado caliente y detección de falla de enfriamiento |
| `Alarmas` | Retardos, avisos, recordatorios y reconocimiento |
| `Historial` | Buffer circular en EEPROM sin puntero (no desgasta una celda) y páginas para SMS |
| `Gsm` | Máquina de estados del SIM800L con cola, reintentos y recuperación |
| `Mensajes` | Comandos recibidos y mensajes salientes |
| `Pantalla` | LCD con lecturas, objetivo, celdas o potencia y alarmas rotativas |

### Pruebas sin hardware

```sh
./firmware/pruebas/correr.sh
```

Compila el firmware real para la computadora con el hardware simulado (SIM800L que responde como el real, DHT, NTC, LCD y EEPROM). Ejecuta **130 comprobaciones** en los dos modos de salida: seguridad, comandos, PID por etapas y PWM, protección térmica, fallas de sensores, alarmas con retardo y recordatorio, falla de enfriamiento, historial de 48 h, módulo GSM que deja de responder y corte de luz.

## App Android

Requisitos: Android 8.0 o superior, con línea celular. Abre `android/` en Android Studio, o compila con `./gradlew assembleDebug`.

Primer uso: escribe el número del chip del equipo y el PIN. La app pide la configuración y las lecturas.

Kotlin, Jetpack Compose y Material 3. Arquitectura MVVM con un repositorio, Room (lecturas y eventos) y DataStore. Las pruebas del protocolo se ejecutan con `./gradlew testDebugUnitTest`.

> Google Play solo permite permisos de SMS a las apps de mensajería predeterminadas: la app se instala desde el APK.

## Simulador (demostración sin hardware)

`herramientas/simulador` ejecuta el **firmware real** con un modelo térmico de cuarto frío con Peltier (fugas, puerta abierta, falla del ventilador) y lo conecta por SMS simulados con la app en un emulador Android:

```sh
./herramientas/simulador/compilar.sh
ANDROID_HOME=~/Library/Android/sdk python3 herramientas/simulador/puente.py
```

En la app usa el número `6670000001` y el PIN `4321`. Con `echo "VELOCIDAD 60" >> herramientas/simulador/control.txt` pasa una hora por minuto.

## Documentación

- [Protocolo SMS](docs/PROTOCOLO.md)
- [Auditoría de la versión 1](docs/AUDITORIA.md)

## Licencia

[MIT](LICENSE)
