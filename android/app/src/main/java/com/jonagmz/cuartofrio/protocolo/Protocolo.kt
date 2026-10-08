package com.jonagmz.cuartofrio.protocolo

/** Mensajes que envía el equipo. Formato en docs/PROTOCOLO.md. */
sealed interface MensajeEquipo {

    data class Estado(
        val temperatura: Float?,
        val humedad: Int?,
        val ladoCaliente: Float?,
        val potencia: Int,
        val encendido: Boolean,
        val alarmas: Int,
        val objetivo: Float?,
        val senal: Int?,
        val minutosEncendido: Long?,
        /** La protección del lado caliente saltó 3 veces en una hora: celdas apagadas hasta enviar ON. */
        val bloqueoTermico: Boolean = false,
    ) : MensajeEquipo

    data class Configuracion(val ajustes: AjustesEquipo) : MensajeEquipo

    data class PaginaHistorial(
        val pagina: Int,
        val total: Int,
        val intervaloMin: Int,
        val minutosDesdeUltima: Int,
        val restantes: Int,
        val muestras: List<Muestra>,
    ) : MensajeEquipo

    data class Evento(val tipo: String, val detalle: String) : MensajeEquipo

    data class Error(val codigo: String) : MensajeEquipo
}

/** Una muestra del historial guardado en el equipo (promedio de un intervalo). */
data class Muestra(
    val temperatura: Float?,
    val humedad: Int?,
    val ladoCaliente: Int?,
    val potencia: Int,
    val alarmas: Int,
    val marcaInicio: Boolean,
)

/** Bits del campo A. */
object Alarma {
    const val TEMP_ALTA = 0x01
    const val TEMP_BAJA = 0x02
    const val HUM_ALTA = 0x04
    const val HUM_BAJA = 0x08
    const val SENSOR = 0x10
    const val SOBRECALENTADO = 0x20
    const val SENSOR_CALIENTE = 0x40
    const val FALLA_ENFRIAMIENTO = 0x80

    val TODAS = listOf(TEMP_ALTA, TEMP_BAJA, HUM_ALTA, HUM_BAJA, SENSOR, SOBRECALENTADO, SENSOR_CALIENTE, FALLA_ENFRIAMIENTO)

    fun nombre(bit: Int) = when (bit) {
        TEMP_ALTA -> "Temperatura alta"
        TEMP_BAJA -> "Temperatura baja"
        HUM_ALTA -> "Humedad alta"
        HUM_BAJA -> "Humedad baja"
        SENSOR -> "Falla del sensor de ambiente"
        SOBRECALENTADO -> "Lado caliente sobrecalentado"
        SENSOR_CALIENTE -> "Falla del sensor del lado caliente"
        FALLA_ENFRIAMIENTO -> "Falla de enfriamiento"
        else -> "Alarma desconocida"
    }

    fun nombres(mascara: Int) = TODAS.filter { mascara and it != 0 }.map(::nombre)
}

object Protocolo {
    private const val PREFIJO = "CF2;"

    /** Devuelve null si el texto no es un mensaje del equipo. */
    fun leer(texto: String): MensajeEquipo? {
        val limpio = texto.trim()
        if (!limpio.startsWith(PREFIJO)) return null
        val partes = limpio.split(';')
        if (partes.size < 2) return null
        return runCatching {
            when (partes[1]) {
                "R" -> leerEstado(campos(partes.drop(2), CLAVES_ESTADO))
                "C" -> MensajeEquipo.Configuracion(AjustesEquipo.desdeCampos(campos(partes.drop(2), AjustesEquipo.CLAVES + "M")))
                "H" -> leerHistorial(partes)
                "E" -> MensajeEquipo.Evento(partes.getOrElse(2) { "" }, partes.getOrElse(3) { "" })
                "X" -> MensajeEquipo.Error(partes.getOrElse(2) { "" })
                else -> null
            }
        }.getOrNull()
    }

    private val CLAVES_ESTADO = listOf("T", "H", "C", "P", "E", "A", "S", "Q", "U", "B")

    /** "SP4.0" -> ("SP", "4.0"): se prueba primero con las claves de dos letras. */
    private fun campos(tokens: List<String>, claves: List<String>): Map<String, String> {
        val ordenadas = claves.sortedByDescending { it.length }
        return tokens.mapNotNull { token ->
            ordenadas.firstOrNull { token.startsWith(it) }?.let { it to token.removePrefix(it) }
        }.toMap()
    }

    private fun decimal(v: String?) = v?.takeIf { it != "--" }?.toFloatOrNull()

    private fun leerEstado(c: Map<String, String>) = MensajeEquipo.Estado(
        temperatura = decimal(c["T"]),
        humedad = c["H"]?.takeIf { it != "--" }?.toIntOrNull(),
        ladoCaliente = decimal(c["C"]),
        potencia = c.getValue("P").toInt(),
        encendido = c["E"] == "1",
        alarmas = c.getValue("A").toInt(16),
        objetivo = decimal(c["S"]),
        senal = c["Q"]?.toIntOrNull()?.takeIf { it != 99 },
        minutosEncendido = c["U"]?.toLongOrNull(),
        bloqueoTermico = c["B"] == "1",
    )

    private fun leerHistorial(partes: List<String>): MensajeEquipo.PaginaHistorial {
        val (pagina, total) = partes[2].split('/').map(String::toInt)
        val c = campos(partes.subList(3, partes.size - 1), listOf("I", "E", "N"))
        return MensajeEquipo.PaginaHistorial(
            pagina = pagina,
            total = total,
            intervaloMin = c.getValue("I").toInt(),
            minutosDesdeUltima = c.getValue("E").toInt(),
            restantes = c.getValue("N").toInt(),
            muestras = decodificarMuestras(partes.last()),
        )
    }

    fun decodificarMuestras(base64: String): List<Muestra> {
        val bytes = base64Url(base64)
        return (0 until bytes.size / 4).map { i ->
            val t = bytes[i * 4].toInt()          // con signo: medios grados
            val h = bytes[i * 4 + 1].toInt() and 0xFF
            val c = bytes[i * 4 + 2].toInt() and 0xFF
            val f = bytes[i * 4 + 3].toInt() and 0xFF
            val marca = t == -128 && h == 254
            Muestra(
                temperatura = if (t == -128) null else t / 2f,
                humedad = if (h > 100) null else h,
                ladoCaliente = if (c == 255) null else c,
                potencia = Math.round((f and 0x0F) * 100f / 15f),
                alarmas = f shr 4,
                marcaInicio = marca,
            )
        }
    }

    private const val ALFABETO = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    private fun base64Url(texto: String): ByteArray {
        val salida = ArrayList<Byte>(texto.length * 3 / 4)
        var acumulado = 0
        var bits = 0
        for (ch in texto) {
            val v = ALFABETO.indexOf(ch)
            require(v >= 0) { "carácter inválido: $ch" }
            acumulado = (acumulado shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                salida.add(((acumulado shr bits) and 0xFF).toByte())
            }
        }
        return salida.toByteArray()
    }
}
