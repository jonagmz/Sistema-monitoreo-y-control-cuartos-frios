package com.jonagmz.cuartofrio.datos

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.almacen by preferencesDataStore("cuarto_frio")

data class Equipo(val nombre: String, val numero: String, val pin: String) {
    val configurado get() = numero.filter(Char::isDigit).length >= 10
}

/** Último mensaje recibido de cada tipo, tal como llegó, con su hora. */
data class Recibido(val texto: String, val tiempo: Long)

/** Estado del último comando enviado, para mostrar "enviando / esperando respuesta / confirmado". */
data class Pendiente(val descripcion: String, val estado: String, val tiempo: Long) {
    companion object {
        const val ENVIANDO = "enviando"
        const val ENVIADO = "enviado"
        const val CONFIRMADO = "confirmado"
        const val ERROR = "error"
    }
}

class Preferencias(private val context: Context) {
    private object K {
        val NOMBRE = stringPreferencesKey("nombre")
        val NUMERO = stringPreferencesKey("numero")
        val PIN = stringPreferencesKey("pin")
        val ESTADO = stringPreferencesKey("estado")
        val ESTADO_T = longPreferencesKey("estado_t")
        val CONFIG = stringPreferencesKey("config")
        val CONFIG_T = longPreferencesKey("config_t")
        val PEND_DESC = stringPreferencesKey("pend_desc")
        val PEND_ESTADO = stringPreferencesKey("pend_estado")
        val PEND_T = longPreferencesKey("pend_t")
        val PAGINAS = stringSetPreferencesKey("paginas_historial")
    }

    val equipo: Flow<Equipo> = context.almacen.data.map {
        Equipo(it[K.NOMBRE] ?: "Cuarto frío", it[K.NUMERO].orEmpty(), it[K.PIN].orEmpty())
    }
    val estado: Flow<Recibido?> = context.almacen.data.map { recibido(it, K.ESTADO, K.ESTADO_T) }
    val configuracion: Flow<Recibido?> = context.almacen.data.map { recibido(it, K.CONFIG, K.CONFIG_T) }
    val pendiente: Flow<Pendiente?> = context.almacen.data.map { p ->
        p[K.PEND_DESC]?.let { Pendiente(it, p[K.PEND_ESTADO].orEmpty(), p[K.PEND_T] ?: 0) }
    }

    private fun recibido(p: Preferences, k: Preferences.Key<String>, t: Preferences.Key<Long>) =
        p[k]?.let { Recibido(it, p[t] ?: 0) }

    suspend fun equipoActual() = equipo.first()
    suspend fun configuracionActual() = configuracion.first()

    suspend fun guardarEquipo(e: Equipo) = context.almacen.edit {
        it[K.NOMBRE] = e.nombre.trim().ifEmpty { "Cuarto frío" }
        it[K.NUMERO] = e.numero.trim()
        it[K.PIN] = e.pin.trim()
    }

    suspend fun guardarEstado(texto: String, tiempo: Long) = context.almacen.edit { it[K.ESTADO] = texto; it[K.ESTADO_T] = tiempo }
    suspend fun guardarConfiguracion(texto: String, tiempo: Long) = context.almacen.edit { it[K.CONFIG] = texto; it[K.CONFIG_T] = tiempo }

    suspend fun guardarPendiente(p: Pendiente) = context.almacen.edit {
        it[K.PEND_DESC] = p.descripcion; it[K.PEND_ESTADO] = p.estado; it[K.PEND_T] = p.tiempo
    }

    suspend fun actualizarPendiente(estado: String) = context.almacen.edit { if (it.contains(K.PEND_DESC)) it[K.PEND_ESTADO] = estado }

    /** Páginas de historial recibidas que aún esperan al resto de la descarga ("tiempo|texto"). */
    suspend fun paginas(): Set<String> = context.almacen.data.first()[K.PAGINAS].orEmpty()
    suspend fun guardarPaginas(p: Set<String>) = context.almacen.edit { it[K.PAGINAS] = p }

    suspend fun olvidarEquipo() = context.almacen.edit {
        listOf(K.ESTADO, K.ESTADO_T, K.CONFIG, K.CONFIG_T, K.PEND_DESC, K.PEND_ESTADO, K.PEND_T, K.PAGINAS).forEach { k -> it.remove(k) }
    }
}
