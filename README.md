# Sistema de monitoreo y control de cuarto frío

Proyecto de residencia profesional. Un Arduino mide la temperatura y la humedad de un cuarto frío, enciende o apaga la refrigeración según los límites configurados y envía reportes y alarmas por SMS. Una app Android muestra las lecturas y permite cambiar la configuración a distancia, también por SMS. No hace falta internet: basta con señal celular.

```
┌──────────────── Cuarto frío ────────────────┐            ┌──────── Celular ────────┐
│ DHT ─► Arduino Uno ─► relés (refrigeración) │   SMS ◄──► │ App Android             │
│           │  ▲                              │            │ lecturas, alarmas,      │
│      LCD 20x4 SIM800L ◄─────────────────────┼────────────┤ configuración           │
└─────────────────────────────────────────────┘            └─────────────────────────┘
```

## Hardware

| Componente | Conexión |
|---|---|
| Arduino Uno | — |
| Módulo GSM SIM800L | RX del Arduino en pin 7, TX en pin 8 (SoftwareSerial, 9600 bps) |
| Sensor DHT11 o DHT22 | Datos en pin 2 |
| LCD 20x4 con adaptador I2C (0x27) | SDA en A4, SCL en A5 |
| 4 módulos de relé activos en LOW | Pines 3, 4, 5 y 6 |

Recomendaciones:
- **Usa un DHT22.** El DHT11 solo mide de 0 a 50 °C con ±2 °C, así que no sirve cerca o por debajo de 0 °C. El DHT22 mide de -40 a 80 °C con ±0.5 °C. Para congeladores o mayor precisión, un DS18B20 es todavía mejor (requiere cambiar el código de lectura).
- **Alimenta el SIM800L aparte.** Al transmitir pide picos de hasta 2 A a 3.7–4.2 V. Si se alimenta del Arduino, se reinicia al enviar SMS.

## Firmware (Arduino)

1. Instala desde el gestor de librerías del Arduino IDE:
   - *DHT sensor library* (Adafruit) y *Adafruit Unified Sensor*
   - *LiquidCrystal I2C* (Frank de Brabander)
2. Copia `Arduino/cuartoFrio/config.example.h` como `config.h` en la misma carpeta y pon tus datos:
   - `NUMERO_REPORTES`: el celular que recibe reportes y alarmas.
   - `NUMEROS_AUTORIZADOS`: los celulares que pueden cambiar la configuración. **Los SMS de cualquier otro número se ignoran.**
   - `TIPO_SENSOR`: `DHT11` o `DHT22`.

   `config.h` está en `.gitignore` para que los números reales no se suban al repositorio.
3. Abre `Arduino/cuartoFrio/cuartoFrio.ino`, selecciona **Arduino Uno** y súbelo.

### Funcionamiento

- **Control con histéresis:** enciende la refrigeración cuando la temperatura llega al máximo y la apaga al bajar al mínimo.
- **Protección del compresor:** después de apagarse, la refrigeración no vuelve a arrancar antes de 3 minutos. Eso incluye el arranque tras un corte de luz.
- **Configuración guardada en EEPROM:** se conserva aunque se vaya la luz. La primera vez, el sistema arranca apagado hasta recibir una configuración.
- **Alarmas inmediatas:** si aparece o se resuelve una alarma (temperatura o humedad fuera de rango, o fallo del sensor), envía un SMS al momento. Como máximo manda uno cada 5 minutos.
- **Reporte periódico** cada 15 minutos.
- **Fallo del sensor:** tras 3 lecturas fallidas, mantiene el estado actual de la refrigeración, avisa por SMS y muestra "ERROR DE SENSOR" en la pantalla.
- **Watchdog:** si el programa se cuelga, el Arduino se reinicia solo en 8 segundos.

### Pruebas

`Arduino/pruebas/correr.sh` compila el sketch en la computadora, simulando el Arduino, el SIM800L, el sensor, la LCD y la EEPROM, y comprueba 30 escenarios: SMS no autorizados, histéresis, protección del compresor, alarmas, fallo del sensor, corte de luz, etc.

```sh
./Arduino/pruebas/correr.sh
```

## App Android

Requisitos: Android 6.0 o superior, con línea celular capaz de enviar y recibir SMS.

1. Abre `Android/Monitorycontrolcuartofrio` en Android Studio (Ladybug o más reciente) y ejecútala. También puedes compilarla con `./gradlew assembleDebug`.
2. La primera vez:
   - Acepta los permisos de **SMS** y **notificaciones**.
   - Escribe el **número del chip del equipo**. La app solo hace caso a los SMS de ese número.
3. Usa **Pedir lecturas ahora** para recibir un reporte al momento, o **Enviar configuración** para cambiar los límites y encender o apagar las celdas.

La app recibe los reportes aunque esté cerrada, guarda el último y muestra una notificación cuando hay una alarma.

> Google Play solo permite permisos de SMS a las apps de mensajería predeterminadas, así que esta app debe instalarse directamente desde el APK, no desde Play Store.

Pruebas unitarias: `./gradlew testDebugUnitTest`.

## Protocolo SMS

| Dirección | Texto | Ejemplo |
|---|---|---|
| App → equipo | `@*tempMin*tempMax*humMin*humMax*sistema` | `@*2*6*80*95*1` |
| App → equipo | `INFO` (pide un reporte al momento) | `INFO` |
| Equipo → app | `temp,hum,ubicacion,alarmaTempBaja,alarmaTempAlta,alarmaHumBaja,alarmaHumAlta,errorSensor,enfriando,sistema,tempMin,tempMax,humMin,humMax` | `4.5,85.0,Cuarto frio no. 1,0,0,0,0,0,1,1,2,6,80,95` |
| Equipo → app | `ERROR: ...` si la configuración no es válida | — |

Límites: temperatura de -30 a 50 °C, humedad de 0 a 100 %, y el mínimo debe ser menor que el máximo. Los 7 primeros campos del reporte son los del formato original, así que la versión anterior de la app sigue leyéndolos.

## Licencia

[MIT](LICENSE)
