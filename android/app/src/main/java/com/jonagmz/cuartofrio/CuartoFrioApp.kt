package com.jonagmz.cuartofrio

import android.app.Application
import android.content.Context
import com.jonagmz.cuartofrio.datos.BaseDatos
import com.jonagmz.cuartofrio.datos.Preferencias
import com.jonagmz.cuartofrio.datos.Repositorio
import com.jonagmz.cuartofrio.notificaciones.Notificaciones
import com.jonagmz.cuartofrio.sms.EnviadorSms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class CuartoFrioApp : Application() {
    val contenedor by lazy { Contenedor(this) }

    override fun onCreate() {
        super.onCreate()
        contenedor.notificaciones.crearCanales()
    }
}

/** Dependencias de la app (inyección manual: el proyecto es pequeño y no necesita Hilt). */
class Contenedor(context: Context) {
    val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val notificaciones = Notificaciones(context)
    private val baseDatos = BaseDatos.crear(context)
    val repositorio = Repositorio(baseDatos, Preferencias(context), EnviadorSms(context), notificaciones)
}
