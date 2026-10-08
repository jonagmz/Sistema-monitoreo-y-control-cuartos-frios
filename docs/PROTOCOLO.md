# Protocolo SMS v2

Contrato entre el firmware y la app. Los mensajes caben en un SMS (160 caracteres) y usan solo caracteres del alfabeto GSM.

## App → equipo (comandos)

```
CF2 <PIN> <COMANDO> [argumentos]
```

- Mayúsculas y minúsculas dan igual. El PIN se omite si el equipo no tiene PIN configurado.
- Solo se aceptan comandos de los números de `NUMEROS_AUTORIZADOS` (se comparan los últimos 10 dígitos).
- Un número autorizado con el PIN equivocado recibe `CF2;X;PIN`; un número no autorizado no recibe respuesta.

| Comando | Respuesta | Descripción |
|---|---|---|
| `INFO` | `R` | Estado actual |
| `CFG` | `C` | Configuración actual |
| `SET k=v [k=v ...]` | `C` o `X` | Cambia ajustes. Se validan todos antes de aplicar ninguno |
| `ON` / `OFF` | `C` | Enciende o apaga el control (equivale a `SET EN=1` / `SET EN=0`) |
| `HIST [horas]` | `H` (varios) | Historial guardado en el equipo (por defecto 48 h) |
| `ACK` | `R` | Reconoce las alarmas activas: deja de enviar recordatorios hasta que aparezca una nueva |

### Ajustes de `SET`

| Clave | Significado | Rango | Por defecto |
|---|---|---|---|
| `SP` | Temperatura objetivo (°C, pasos de 0.5) | -20 a 30 | 4.0 |
| `TL` / `TH` | Alarma de temperatura baja / alta (°C) | -30 a 50, `TL < SP < TH` | 1.0 / 8.0 |
| `HL` / `HH` | Alarma de humedad baja / alta (%) | 0 a 100, `HL < HH` | 60 / 95 |
| `AD` | Retardo de alarmas de temperatura y humedad (min) | 0 a 120 | 10 |
| `RI` | Intervalo del reporte periódico (min, 0 = desactivado) | 0 o 5 a 1440 | 60 |
| `RA` | Recordatorio de alarmas activas (h, 0 = desactivado) | 0 a 48 | 4 |
| `HI` | Intervalo de muestras del historial (min) | 5 a 60 | 15 |
| `PM` | Potencia máxima (%) | 0 a 100 | 100 |
| `PC` | Temperatura máxima del lado caliente (°C) | 40 a 90 | 65 |
| `KP` / `KI` / `KD` | Parámetros del PID (%/°C, %/(°C·min), %·min/°C) | 0 a 200 / 0 a 50 / 0 a 200 | 20 / 1 / 0 |
| `EN` | Control encendido | 0 o 1 | 0 |

Ejemplo: `CF2 1234 SET SP=3.5 TH=7 AD=15`

## Equipo → app

Todos empiezan con `CF2;<tipo>;` y siguen con campos `clave valor` pegados (`T4.5`), separados por `;`.

### `R`: estado

```
CF2;R;T4.5;H85;C32.1;P45;E1;A0;S4.0;Q21;U1234
```

| Campo | Significado |
|---|---|
| `T` | Temperatura (°C) o `--` si el sensor falla |
| `H` | Humedad (%) o `--` |
| `C` | Temperatura del lado caliente (°C) o `--` (sin sensor o con falla) |
| `P` | Potencia aplicada a las celdas (%) |
| `E` | Control encendido (1) o apagado (0) |
| `A` | Alarmas activas, máscara de bits en hexadecimal (tabla abajo) |
| `S` | Temperatura objetivo (°C) |
| `Q` | Señal del módulo GSM (0 a 31; 99 = desconocida) |
| `U` | Minutos desde el último encendido |

| Bit | Alarma |
|---|---|
| 0x01 | Temperatura alta |
| 0x02 | Temperatura baja |
| 0x04 | Humedad alta |
| 0x08 | Humedad baja |
| 0x10 | Falla del sensor de ambiente |
| 0x20 | Lado caliente sobrecalentado (celdas apagadas por protección) |
| 0x40 | Falla del sensor del lado caliente |
| 0x80 | Falla de enfriamiento (potencia alta mucho tiempo sin que baje la temperatura) |

### `C`: configuración

```
CF2;C;SP4.0;TL1.0;TH8.0;HL60;HH95;AD10;RI60;RA4;HI15;PM100;PC65;KP20;KI1;KD0;EN1;MR
```

Las mismas claves que `SET`, más `M`: `R` = relés por etapas, `P` = MOSFET con PWM.

### `H`: página de historial

```
CF2;H;1/6;I15;E7;N26;<datos>
```

| Campo | Significado |
|---|---|
| `1/6` | Página y total de páginas |
| `I` | Minutos entre muestras |
| `E` | Minutos desde la muestra más reciente (de todo el historial) |
| `N` | Muestras que vienen después de la última de esta página (0 en la última página) |
| datos | Muestras en base64url (`A-Z a-z 0-9 - _`, sin relleno), de la más antigua a la más reciente |

Cada muestra ocupa 4 bytes:

| Byte | Contenido |
|---|---|
| 0 | Temperatura en medios grados, entero con signo (`-128` = sin dato) |
| 1 | Humedad en % (`255` = sin dato) |
| 2 | Lado caliente en °C, sin signo (`255` = sin dato) |
| 3 | Bits 0-3: potencia promedio en 1/15 (0 = 0 %, 15 = 100 %). Bits 4-7: alarmas del intervalo (bit 4 = temperatura fuera de rango, 5 = falla de sensor, 6 = lado caliente, 7 = falla de enfriamiento) |

Hora de cada muestra: `hora de recepción - E - (N + muestras posteriores en la página) × I` minutos.

### `E`: evento

```
CF2;E;INICIO;LUZ
```

| Evento | Detalle |
|---|---|
| `INICIO` | `LUZ` (volvió la energía tras un corte) o `REINICIO` (watchdog o botón de reset) |

### `X`: error

```
CF2;X;<código>
```

| Código | Causa |
|---|---|
| `PIN` | PIN incorrecto |
| `CMD` | Comando desconocido |
| `VAL` | Algún valor de `SET` es inválido o fuera de rango |
