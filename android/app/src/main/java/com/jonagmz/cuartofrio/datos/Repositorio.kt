package com.jonagmz.cuartofrio.datos

import com.jonagmz.cuartofrio.notificaciones.Notificaciones
import com.jonagmz.cuartofrio.protocolo.AjustesEquipo
import com.jonagmz.cuartofrio.protocolo.Alarma
import com.jonagmz.cuartofrio.protocolo.Comandos
import com.jonagmz.cuartofrio.protocolo.MensajeEquipo
import com.jonagmz.cuartofrio.protocolo.Protocolo
import com.jonagmz.cuartofrio.sms.EnviadorSms
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class EstadoEquipo(val datos: MensajeEquipo.Estado, val tiempo: Long)
data class ConfiguracionEquipo(val ajustes: AjustesEquipo, val tiempo: Long)

class Repositorio(
    private val db: BaseDatos,
    private val prefs: Preferencias,
    private val sms: EnviadorSms,
    private val avisos: Notificaciones,
    private val reloj: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    val equipo: Flow<Equipo> = prefs.equipo
    val pendiente: Flow<Pendiente?> = prefs.pendiente
    val eventos: Flow<List<Evento>> = db.eventos().recientes()
    val estado: Flow<EstadoEquipo?> = prefs.estado.map { r ->
        r?.let { (Protocolo.leer(it.texto) as? MensajeEquipo.Estado)?.let { e -> EstadoEquipo(e, it.tiempo) } }
    }
    val configuracion: Flow<ConfiguracionEquipo?> = prefs.configuracion.map { r ->
        r?.let { (Protocolo.leer(it.texto) as? MensajeEquipo.Configuracion)?.let { c -> ConfiguracionEquipo(c.ajustes, it.tiempo) } }
    }

    fun lecturasDesde(desde: Long): Flow<List<Lectura>> = db.lecturas().desde(desde)
    suspend fun todasLasLecturas() = db.lecturas().todas()

    // ------------------------------------------------------------------ SMS recibidos

    /** Procesa un SMS. Devuelve false si no es del equipo configurado o no es un mensaje del protocolo. */
    suspend fun procesarSms(numero: String?, texto: String, hora: Long = reloj()): Boolean {
        val equipo = prefs.equipoActual()
        if (!Comandos.mismoNumero(numero, equipo.numero)) return false
        val mensaje = Protocolo.leer(texto) ?: return false
        mutex.withLock {
            when (mensaje) {
                is MensajeEquipo.Estado -> recibirEstado(mensaje, texto, hora, equipo.nombre)
                is MensajeEquipo.Configuracion -> {
                    prefs.guardarConfiguracion(texto.trim(), hora)
                    prefs.actualizarPendiente(Pendiente.CONFIRMADO)
                    evento(hora, Evento.CONFIGURACION, "Configuración recibida", resumen(mensaje.ajustes))
                }
                is MensajeEquipo.PaginaHistorial -> recibirPaginaHistorial(mensaje, texto, hora)
                is MensajeEquipo.Evento -> {
                    val titulo = if (mensaje.detalle == "LUZ") "Volvió la luz: el equipo se reinició" else "El equipo se reinició"
                    val detalle = if (mensaje.detalle == "LUZ") "Hubo un corte de energía. Revisa el historial para ver cuánto duró." else "Reinicio por watchdog o botón de reset."
                    evento(hora, Evento.INICIO, titulo, detalle)
                    avisos.aviso(equipo.nombre, titulo)
                }
                is MensajeEquipo.Error -> {
                    val texto = when (mensaje.codigo) {
                        "PIN" -> "PIN incorrecto: el equipo rechazó el comando"
                        "VAL" -> "El equipo rechazó los valores (fuera de rango)"
                        "CMD" -> "El equipo no reconoció el comando"
                        else -> "Error ${mensaje.codigo}"
                    }
                    prefs.guardarPendiente(Pendiente(texto, Pendiente.ERROR, hora))
                    evento(hora, Evento.ERROR, texto)
                }
            }
        }
        return true
    }

    private suspend fun recibirEstado(e: MensajeEquipo.Estado, texto: String, hora: Long, nombre: String) {
        val previo = estado.first()?.datos
        val anteriores = previo?.alarmas ?: 0
        prefs.guardarEstado(texto.trim(), hora)
        prefs.actualizarPendiente(Pendiente.CONFIRMADO)
        db.lecturas().insertar(listOf(Lectura(
            clave = "R${hora / 1000}", tiempo = hora, temperatura = e.temperatura, humedad = e.humedad?.toFloat(),
            ladoCaliente = e.ladoCaliente, potencia = e.potencia, alarmas = e.alarmas, origen = Lectura.REPORTE,
        )))
        val nuevas = e.alarmas and anteriores.inv()
        val resueltas = anteriores and e.alarmas.inv()
        Alarma.nombres(nuevas).forEach { evento(hora, Evento.ALARMA, it, detalleAlarma(e)) }
        Alarma.nombres(resueltas).forEach { evento(hora, Evento.ALARMA_RESUELTA, "$it: resuelta") }
        if (nuevas != 0) avisos.alarma(nombre, Alarma.nombres(nuevas).joinToString(" · "), detalleAlarma(e))
        if (e.bloqueoTermico && previo?.bloqueoTermico != true) {
            evento(hora, Evento.ALARMA, "Celdas bloqueadas", "El disipador se sobrecalentó 3 veces en una hora. Revisa su ventilador y reanuda desde el panel.")
            avisos.alarma(nombre, "Celdas bloqueadas", "El disipador se sobrecalentó 3 veces en una hora. Revisa el ventilador y reanuda desde la app.")
        } else if (!e.bloqueoTermico && previo?.bloqueoTermico == true) {
            evento(hora, Evento.ALARMA_RESUELTA, "Bloqueo térmico quitado: las celdas vuelven a enfriar")
        }
    }

    private fun detalleAlarma(e: MensajeEquipo.Estado) = buildString {
        append("Temperatura ${e.temperatura?.let { "%.1f °C".format(it) } ?: "--"}")
        e.humedad?.let { append(" · Humedad $it %") }
        e.ladoCaliente?.let { append(" · Lado caliente %.0f °C".format(it)) }
    }

    /**
     * Las páginas se guardan hasta que llega la última (que trae las muestras más recientes) y se procesan juntas:
     * así se sabe si hubo un reinicio en medio. Las muestras anteriores al último reinicio no tienen hora fiable
     * (no se sabe cuánto duró el corte) y se descartan; normalmente ya se habían descargado antes.
     */
    private suspend fun recibirPaginaHistorial(p: MensajeEquipo.PaginaHistorial, texto: String, hora: Long) {
        val guardadas = prefs.paginas().filter { hora - it.substringBefore('|').toLong() < 30 * 60_000L }.toMutableSet()
        guardadas += "$hora|${texto.trim()}"
        if (p.pagina < p.total) {
            prefs.guardarPaginas(guardadas)
            return
        }
        val lote = guardadas.mapNotNull { linea ->
            val t = linea.substringBefore('|').toLong()
            (Protocolo.leer(linea.substringAfter('|')) as? MensajeEquipo.PaginaHistorial)
                ?.takeIf { it.total == p.total && it.intervaloMin == p.intervaloMin }?.let { it to t }
        }.distinctBy { it.first.pagina }
        prefs.guardarPaginas(emptySet())

        data class Ubicada(val desdeElFinal: Int, val tiempo: Long, val muestra: com.jonagmz.cuartofrio.protocolo.Muestra, val intervalo: Int)
        val ubicadas = lote.flatMap { (pagina, recibida) ->
            pagina.muestras.mapIndexed { i, m ->
                val desdeElFinal = pagina.restantes + (pagina.muestras.size - 1 - i)
                val minutos = pagina.minutosDesdeUltima + desdeElFinal.toLong() * pagina.intervaloMin
                Ubicada(desdeElFinal, recibida - minutos * 60_000L, m, pagina.intervaloMin)
            }
        }
        val ultimoReinicio = ubicadas.filter { it.muestra.marcaInicio }.minOfOrNull { it.desdeElFinal } ?: Int.MAX_VALUE
        val lecturas = ubicadas.filter { !it.muestra.marcaInicio && it.desdeElFinal < ultimoReinicio }.map {
            Lectura(
                clave = "H${it.tiempo / (it.intervalo * 60_000L)}", tiempo = it.tiempo,
                temperatura = it.muestra.temperatura, humedad = it.muestra.humedad?.toFloat(),
                ladoCaliente = it.muestra.ladoCaliente?.toFloat(), potencia = it.muestra.potencia,
                alarmas = if (it.muestra.alarmas != 0) 0x100 or it.muestra.alarmas else 0, origen = Lectura.HISTORIAL,
            )
        }
        db.lecturas().insertar(lecturas)
        prefs.actualizarPendiente(Pendiente.CONFIRMADO)
        evento(hora, Evento.COMANDO, "Historial descargado", "${lecturas.size} muestras de ${lote.size} de ${p.total} SMS")
    }

    private suspend fun evento(tiempo: Long, tipo: String, titulo: String, detalle: String = "") =
        db.eventos().insertar(Evento(tiempo = tiempo, tipo = tipo, titulo = titulo, detalle = detalle))

    private fun resumen(a: AjustesEquipo) =
        "Objetivo ${AjustesEquipo.decimal(a.objetivo)} °C · alarmas ${AjustesEquipo.decimal(a.alarmaTempBaja)} a " +
            "${AjustesEquipo.decimal(a.alarmaTempAlta)} °C · ${if (a.encendido) "encendido" else "apagado"}"

    // ------------------------------------------------------------------ comandos

    sealed interface Resultado {
        data object Enviado : Resultado
        data object SinCambios : Resultado
        data class Error(val mensaje: String) : Resultado
    }

    private suspend fun enviar(descripcion: String, textos: List<String>): Resultado {
        val equipo = prefs.equipoActual()
        if (!equipo.configurado) return Resultado.Error("Configura primero el número del equipo")
        if (textos.isEmpty()) return Resultado.SinCambios
        val hora = reloj()
        prefs.guardarPendiente(Pendiente(descripcion, Pendiente.ENVIANDO, hora))
        val error = sms.enviar(equipo.numero, textos)
        if (error != null) {
            prefs.guardarPendiente(Pendiente(descripcion, Pendiente.ERROR, hora))
            return Resultado.Error(error)
        }
        evento(hora, Evento.COMANDO, descripcion)
        return Resultado.Enviado
    }

    private suspend fun pin() = prefs.equipoActual().pin

    suspend fun pedirEstado() = enviar("Pedir lecturas", listOf(Comandos.info(pin())))
    suspend fun pedirConfiguracion() = enviar("Leer configuración", listOf(Comandos.configuracion(pin())))
    suspend fun reconocerAlarmas() = enviar("Reconocer alarmas", listOf(Comandos.reconocer(pin())))
    suspend fun reanudar() = enviar("Reanudar tras bloqueo térmico", listOf(Comandos.encender(pin(), true)))
    suspend fun descargarHistorial(horas: Int = 48) = enviar("Descargar historial ($horas h)", listOf(Comandos.historial(pin(), horas)))
    suspend fun encender(encendido: Boolean) =
        enviar(if (encendido) "Encender control" else "Apagar control", listOf(Comandos.encender(pin(), encendido)))

    suspend fun enviarAjustes(nuevos: AjustesEquipo): Resultado {
        val errores = nuevos.validar()
        if (errores.isNotEmpty()) return Resultado.Error(errores.first())
        val actuales = configuracion.first()?.ajustes
        return enviar("Enviar configuración", Comandos.cambios(pin(), actuales, nuevos))
    }

    suspend fun resultadoEnvio(exito: Boolean) {
        prefs.actualizarPendiente(if (exito) Pendiente.ENVIADO else Pendiente.ERROR)
    }

    // ------------------------------------------------------------------ equipo

    suspend fun guardarEquipo(e: Equipo) {
        val anterior = prefs.equipoActual()
        if (!Comandos.mismoNumero(anterior.numero, e.numero) && anterior.configurado) prefs.olvidarEquipo()
        prefs.guardarEquipo(e)
    }

    suspend fun borrarDatosLocales() {
        db.lecturas().borrar()
        db.eventos().borrar()
    }
}
