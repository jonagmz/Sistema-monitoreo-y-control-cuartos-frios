package com.jonagmz.cuartofrio.protocolo

import java.util.Locale

/** Ajustes del equipo, con los mismos rangos que el firmware (Ajustes.cpp). */
data class AjustesEquipo(
    val objetivo: Float = 4f,
    val alarmaTempBaja: Float = 1f,
    val alarmaTempAlta: Float = 8f,
    val alarmaHumBaja: Int = 60,
    val alarmaHumAlta: Int = 95,
    val retardoAlarmaMin: Int = 10,
    val reporteMin: Int = 60,
    val recordatorioH: Int = 4,
    val historialMin: Int = 15,
    val potenciaMax: Int = 100,
    val maxLadoCaliente: Int = 65,
    val kp: Float = 20f,
    val ki: Float = 1f,
    val kd: Float = 0f,
    val encendido: Boolean = false,
    val modo: Char = 'R',
) {
    /** Claves del protocolo con su valor formateado como lo espera el firmware. */
    fun comoCampos(): Map<String, String> = linkedMapOf(
        "SP" to decimal(objetivo), "TL" to decimal(alarmaTempBaja), "TH" to decimal(alarmaTempAlta),
        "HL" to "$alarmaHumBaja", "HH" to "$alarmaHumAlta", "AD" to "$retardoAlarmaMin", "RI" to "$reporteMin",
        "RA" to "$recordatorioH", "HI" to "$historialMin", "PM" to "$potenciaMax", "PC" to "$maxLadoCaliente",
        "KP" to decimal(kp), "KI" to decimal(ki), "KD" to decimal(kd), "EN" to if (encendido) "1" else "0",
    )

    /** Lista de errores (vacía si todo está bien). */
    fun validar(): List<String> = buildList {
        if (objetivo !in -20f..30f || Math.round(objetivo * 10) % 5 != 0) add("El objetivo debe estar entre -20 y 30 °C, en pasos de 0.5")
        if (alarmaTempBaja !in -30f..50f || alarmaTempAlta !in -30f..50f) add("Las alarmas de temperatura deben estar entre -30 y 50 °C")
        if (!(alarmaTempBaja < objetivo && objetivo < alarmaTempAlta)) add("Debe cumplirse: alarma baja < objetivo < alarma alta")
        if (alarmaHumBaja !in 0..100 || alarmaHumAlta !in 0..100 || alarmaHumBaja >= alarmaHumAlta) add("Humedad: 0 a 100 %, mínima menor que máxima")
        if (retardoAlarmaMin !in 0..120) add("El retardo de alarmas va de 0 a 120 min")
        if (reporteMin != 0 && reporteMin !in 5..1440) add("El reporte periódico va de 5 a 1440 min (0 = desactivado)")
        if (recordatorioH !in 0..48) add("El recordatorio va de 0 a 48 h")
        if (historialMin !in 5..60) add("El intervalo del historial va de 5 a 60 min")
        if (potenciaMax !in 0..100) add("La potencia máxima va de 0 a 100 %")
        if (maxLadoCaliente !in 40..90) add("El máximo del lado caliente va de 40 a 90 °C")
        if (kp !in 0f..200f || ki !in 0f..50f || kd !in 0f..200f) add("PID fuera de rango (KP 0-200, KI 0-50, KD 0-200)")
    }

    companion object {
        val CLAVES = listOf("SP", "TL", "TH", "HL", "HH", "AD", "RI", "RA", "HI", "PM", "PC", "KP", "KI", "KD", "EN")

        fun decimal(v: Float): String = String.format(Locale.US, "%.1f", v)

        fun desdeCampos(c: Map<String, String>) = AjustesEquipo(
            objetivo = c.getValue("SP").toFloat(),
            alarmaTempBaja = c.getValue("TL").toFloat(),
            alarmaTempAlta = c.getValue("TH").toFloat(),
            alarmaHumBaja = c.getValue("HL").toInt(),
            alarmaHumAlta = c.getValue("HH").toInt(),
            retardoAlarmaMin = c.getValue("AD").toInt(),
            reporteMin = c.getValue("RI").toInt(),
            recordatorioH = c.getValue("RA").toInt(),
            historialMin = c.getValue("HI").toInt(),
            potenciaMax = c.getValue("PM").toInt(),
            maxLadoCaliente = c.getValue("PC").toInt(),
            kp = c.getValue("KP").toFloat(),
            ki = c.getValue("KI").toFloat(),
            kd = c.getValue("KD").toFloat(),
            encendido = c["EN"] == "1",
            modo = c["M"]?.firstOrNull() ?: 'R',
        )
    }
}
