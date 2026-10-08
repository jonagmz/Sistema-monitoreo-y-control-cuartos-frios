# Auditoría y mejoras (octubre 2026)

Revisión del firmware de Arduino y de la app Android. Severidad: 🔴 crítica · 🟠 alta · 🟡 media · ⚪ baja.

## Firmware (`Arduino/cuartoFrio/cuartoFrio.ino`)

| # | Sev. | Problema | Solución |
|---|---|---|---|
| 1 | 🔴 | **No compilaba:** decía `#id DEV` en lugar de `#if DEV`, y en el SMS faltaban un `+` y un `;` (`',' alarmaBajaHum`). | Corregido. Ahora compila en Arduino Uno (45 % de flash, 56 % de RAM). |
| 2 | 🔴 | **Cualquier persona podía controlar el cuarto frío:** bastaba enviar un SMS con `@` desde cualquier número para cambiar los límites o apagar la refrigeración. | Solo se aceptan órdenes de los números de `NUMEROS_AUTORIZADOS`. |
| 3 | 🔴 | **Escribía en pines equivocados:** el bucle `for (i = 0; i < 7; i++) digitalWrite(i, …)` tocaba el pin 2 (sensor DHT) y los pines 0 y 1 (puerto serie), además de los relés. | Solo se escriben los pines de los relés (3 a 6). |
| 4 | 🟠 | Si el sensor fallaba, `readTemperature()` devolvía NaN y se usaba como temperatura para controlar. | Se detecta el fallo tras 3 lecturas: alarma por SMS, aviso en la LCD y "ND" en el reporte. |
| 5 | 🟠 | Los límites se perdían en cada corte de luz (empezaban en 0). | Se guardan en EEPROM. |
| 6 | 🟠 | Las alarmas solo se avisaban en el reporte de cada 15 minutos. | Se envía un SMS en cuanto aparece o se resuelve una alarma (como máximo uno cada 5 minutos). |
| 7 | 🟠 | Sin protección del compresor: podía arrancar y parar seguido, incluso al volver la luz. | Tiempo mínimo de 3 minutos apagado antes de volver a arrancar. |
| 8 | 🟠 | Si el programa se colgaba (I2C, GSM), el cuarto frío quedaba sin control. | Watchdog de 8 segundos. |
| 9 | 🟡 | No se validaba la configuración (el mínimo podía ser mayor que el máximo, o recibir valores vacíos). | Se valida igual que en la app y se responde `ERROR` si no es válida. |
| 10 | 🟡 | El sensor y la LCD se actualizaban en cada vuelta del loop. Leer el DHT desactiva interrupciones, y SoftwareSerial perdía caracteres de los SMS entrantes. | El sensor se lee cada 2 s y nunca mientras está llegando un SMS; la LCD se refresca cada segundo. |
| 11 | 🟡 | La LCD dejaba caracteres viejos cuando los números se acortaban (de "10" a "9"). | Cada renglón se rellena hasta 20 caracteres. |
| 12 | 🟡 | El DHT11 no mide por debajo de 0 °C (±2 °C de precisión). | Configurable a DHT22 en `config.h` (cambio de hardware recomendado). |
| 13 | ⚪ | Número de teléfono escrito en el código. | Movido a `config.h`, que no se sube a git. |
| 14 | ⚪ | Dependía de las librerías `Separador` (no está en el gestor de Arduino) y `elapsedMillis`, y usaba mucho `String` (fragmenta los 2 KB de RAM del Uno). | Ya no se necesitan; se usan buffers fijos. |
| 15 | ⚪ | Comentarios incorrectos (pines "RX(8) TX(9)", "contiene la palabra Info"). | Corregidos. |

## App Android (`Android/Monitorycontrolcuartofrio`)

| # | Sev. | Problema | Solución |
|---|---|---|---|
| 16 | 🔴 | **No compilaba con herramientas actuales:** Gradle 3.3, Android Gradle Plugin 2.3.3 (requiere Java 8), repositorio jcenter (cerrado) y librerías de soporte obsoletas. | Gradle 8.9, AGP 8.7, AndroidX, compileSdk/targetSdk 35 y Java 17. |
| 17 | 🔴 | **Se cerraba con cualquier SMS** que no fuera del equipo (`ArrayIndexOutOfBoundsException` al leer `DatosDiv[7]`). | Solo se procesan los SMS del número del equipo, y el reporte se valida antes de usarlo. |
| 18 | 🟠 | **La alarma de temperatura baja nunca se veía:** el azul se ponía y enseguida se sobrescribía con negro al revisar la alarma alta (igual con la humedad). | El color se calcula una sola vez: rojo si es alta, azul si es baja. |
| 19 | 🟠 | Con la app cerrada no se recibían los reportes ni se avisaba de las alarmas. | El receptor guarda el último reporte y muestra una notificación cuando hay alarma. |
| 20 | 🟠 | En Android 12 o superior se cerraba al enviar un SMS (`PendingIntent` sin `FLAG_IMMUTABLE`). En Android 14 o superior, los receptores registrados sin `RECEIVER_EXPORTED/NOT_EXPORTED` también la cerraban. | Corregido. |
| 21 | 🟠 | Cualquier app podía enviarle un SMS falso: el receptor no exigía permiso, y el aviso interno era una difusión implícita que cualquier app podía leer o imitar. | El receptor exige `BROADCAST_SMS` (solo el sistema puede usarlo) y el aviso interno es explícito al propio paquete. |
| 22 | 🟡 | Si no se tocaba el switch, se enviaba `null` como estado. | Se usa el estado real del switch. |
| 23 | 🟡 | No se podían escribir temperaturas negativas, y no se validaban campos vacíos ni que el mínimo fuera menor que el máximo. | `numberSigned` y validación con mensajes claros. |
| 24 | 🟡 | Cada envío registraba dos receptores nuevos sin quitarlos nunca (fuga de memoria y avisos repetidos). | Se registran una sola vez. |
| 25 | 🟡 | Pedía permiso y enviaba el SMS sin esperar la respuesta; además, nunca pedía `RECEIVE_SMS`. | Se piden todos los permisos al inicio y no se envía nada sin permiso. |
| 26 | ⚪ | Número del equipo escrito en el código. | Se configura desde la app. |
| 27 | ⚪ | No se sabía de cuándo era la última lectura. | Se muestra la fecha y hora. |
| 28 | ⚪ | Dependencia sin usar (`play-services-maps`), permiso innecesario (`READ_SMS`), diseño con medidas fijas que se cortaba en algunas pantallas y textos con faltas ("desesada"). | Eliminados y corregidos; diseño adaptable con scroll. |

## Verificación

- Firmware: compila con `arduino-cli` para Arduino Uno, y la simulación `Arduino/pruebas/correr.sh` pasa 30 de 30 comprobaciones.
- App: `./gradlew assembleDebug testDebugUnitTest lintDebug` termina sin errores; pasan 7 tests unitarios y lint no da errores.
- No se probó en hardware real ni en un teléfono.

## Pendiente (requiere decisión o hardware)

- **Datos personales en el historial de git:** los números de teléfono que estaban en el código siguen en commits antiguos de un repositorio público. Para borrarlos hay que reescribir el historial (`git filter-repo`) y forzar el push; si es posible, también conviene cambiar esos números.
- **Sensor:** cambiar el DHT11 por un DHT22 o un DS18B20.
- **Fuente del SIM800L:** debe dar 2 A en picos.
- **Comprobar que los SMS llegan:** el firmware no confirma que cada SMS se haya enviado (respuesta `+CMGS`). Se podría reintentar en caso de fallo.
- **Varios cuartos fríos:** la app maneja un solo equipo; para varios habría que guardar una lista de números.
