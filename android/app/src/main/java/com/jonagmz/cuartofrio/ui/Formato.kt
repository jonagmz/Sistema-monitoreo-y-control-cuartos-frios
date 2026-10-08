package com.jonagmz.cuartofrio.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Formato {
    private val zona get() = ZoneId.systemDefault()
    private val es = Locale.forLanguageTag("es-MX")
    private val hora = DateTimeFormatter.ofPattern("HH:mm", es)
    private val fechaCorta = DateTimeFormatter.ofPattern("d MMM", es)
    private val fechaLarga = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", es)

    fun temperatura(t: Float?) = t?.let { String.format(es, "%.1f °C", it) } ?: "--"
    fun numero(t: Float?) = t?.let { String.format(es, "%.1f", it) } ?: "--"

    fun horaDe(t: Long): String = hora.format(Instant.ofEpochMilli(t).atZone(zona))

    fun fechaHora(t: Long): String {
        val z = Instant.ofEpochMilli(t).atZone(zona)
        return when (z.toLocalDate()) {
            LocalDate.now(zona) -> "hoy ${hora.format(z)}"
            LocalDate.now(zona).minusDays(1) -> "ayer ${hora.format(z)}"
            else -> "${fechaCorta.format(z)} ${hora.format(z)}"
        }
    }

    fun dia(t: Long): String {
        val d = Instant.ofEpochMilli(t).atZone(zona).toLocalDate()
        return when (d) {
            LocalDate.now(zona) -> "Hoy"
            LocalDate.now(zona).minusDays(1) -> "Ayer"
            else -> fechaLarga.format(d).replaceFirstChar { it.uppercase() }
        }
    }

    fun etiquetaEje(t: Long, rangoMs: Long): String {
        val z = Instant.ofEpochMilli(t).atZone(zona)
        return if (rangoMs <= 2 * 86_400_000L) hora.format(z) else fechaCorta.format(z)
    }

    fun relativo(t: Long, ahora: Long): String {
        val s = (ahora - t) / 1000
        return when {
            s < 60 -> "hace un momento"
            s < 3600 -> "hace ${s / 60} min"
            s < 86_400 -> "hace ${s / 3600} h"
            else -> fechaHora(t)
        }
    }

    fun duracion(minutos: Long): String = when {
        minutos < 60 -> "$minutos min"
        minutos < 1440 -> "${minutos / 60} h ${minutos % 60} min"
        else -> "${minutos / 1440} d ${minutos % 1440 / 60} h"
    }

    fun calidadSenal(csq: Int?): String = when {
        csq == null -> "Sin dato"
        csq >= 20 -> "Excelente"
        csq >= 15 -> "Buena"
        csq >= 10 -> "Regular"
        else -> "Débil"
    }
}

/** Hora actual que se actualiza sola para los textos "hace X min". */
@Composable
fun ahoraQueAvanza(cadaMs: Long = 30_000): Long {
    val ahora by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(cadaMs)
            value = System.currentTimeMillis()
        }
    }
    return ahora
}
