@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalCoroutinesApi::class)

package com.jonagmz.cuartofrio.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeviceThermostat
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingFlat
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.jonagmz.cuartofrio.datos.ConfiguracionEquipo
import com.jonagmz.cuartofrio.datos.Equipo
import com.jonagmz.cuartofrio.datos.EstadoEquipo
import com.jonagmz.cuartofrio.datos.Lectura
import com.jonagmz.cuartofrio.datos.Pendiente
import com.jonagmz.cuartofrio.datos.Repositorio
import com.jonagmz.cuartofrio.protocolo.Alarma
import com.jonagmz.cuartofrio.ui.EstadoComando
import com.jonagmz.cuartofrio.ui.Formato
import com.jonagmz.cuartofrio.ui.ahoraQueAvanza
import com.jonagmz.cuartofrio.ui.componentes.Grafica
import com.jonagmz.cuartofrio.ui.componentes.Punto
import com.jonagmz.cuartofrio.ui.componentes.Serie
import com.jonagmz.cuartofrio.ui.tema.ColoresEstado
import com.jonagmz.cuartofrio.ui.vm
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val HORAS_RECIENTES = 6

data class PanelUi(
    val equipo: Equipo,
    val estado: EstadoEquipo?,
    val configuracion: ConfiguracionEquipo?,
    val pendiente: Pendiente?,
    val recientes: List<Lectura>,
) {
    /** ON/OFF responde con la configuración: si es más nueva que el último estado, manda ella. */
    val encendido: Boolean
        get() = if (configuracion != null && configuracion.tiempo > (estado?.tiempo ?: 0)) configuracion.ajustes.encendido
        else estado?.datos?.encendido ?: false
}

class PanelViewModel(private val repo: Repositorio) : ViewModel() {
    private val desde = flow {
        while (true) {
            emit(System.currentTimeMillis() - HORAS_RECIENTES * 3_600_000L)
            delay(5 * 60_000L)
        }
    }

    val ui: StateFlow<PanelUi?> = combine(
        repo.equipo, repo.estado, repo.configuracion, repo.pendiente, desde.flatMapLatest { repo.lecturasDesde(it) },
    ) { equipo, estado, config, pendiente, recientes -> PanelUi(equipo, estado, config, pendiente, recientes) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val mensajes = MutableSharedFlow<String>(extraBufferCapacity = 4)

    fun pedirLecturas() = lanzar { repo.pedirEstado() }
    fun descargarHistorial() = lanzar { repo.descargarHistorial() }
    fun reconocer() = lanzar { repo.reconocerAlarmas() }
    fun reanudar() = lanzar { repo.reanudar() }
    fun encender(encendido: Boolean) = lanzar { repo.encender(encendido) }

    private fun lanzar(accion: suspend () -> Repositorio.Resultado) = viewModelScope.launch {
        when (val r = accion()) {
            is Repositorio.Resultado.Error -> mensajes.emit(r.mensaje)
            else -> Unit
        }
    }
}

@Composable
fun PantallaPanel(irAHistorial: () -> Unit) {
    val vm = vm { PanelViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val avisos = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.mensajes.collect { avisos.showSnackbar(it) } }
    val ahora = ahoraQueAvanza()
    var confirmarEncendido by remember { mutableStateOf<Boolean?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(ui?.equipo?.nombre ?: "Cuarto frío", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        ui?.estado?.let {
                            Text("Actualizado ${Formato.relativo(it.tiempo, ahora)}", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                actions = { IconButton(onClick = vm::pedirLecturas) { Icon(Icons.Rounded.Refresh, "Pedir lecturas") } },
            )
        },
        snackbarHost = { SnackbarHost(avisos) },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) { relleno ->
        val u = ui ?: return@Scaffold
        val esperando = u.pendiente?.estado in listOf(Pendiente.ENVIANDO, Pendiente.ENVIADO) && ahora - (u.pendiente?.tiempo ?: 0) < 3 * 60_000
        PullToRefreshBox(isRefreshing = esperando, onRefresh = vm::pedirLecturas, modifier = Modifier.padding(relleno).fillMaxSize()) {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { EstadoComando(u.pendiente, ahora, Modifier.fillMaxWidth()) }
                if (u.estado == null) {
                    item { SinDatos(vm::pedirLecturas) }
                    return@LazyColumn
                }
                val e = u.estado.datos
                item { TarjetaTemperatura(u, ahora) }
                if (e.bloqueoTermico) item { TarjetaBloqueo(vm::reanudar) }
                if (e.alarmas != 0) item { TarjetaAlarmas(e.alarmas, vm::reconocer) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Indicador(Icons.Rounded.WaterDrop, "Humedad", e.humedad?.let { "$it %" } ?: "--",
                            u.configuracion?.let { "Alarmas: ${it.ajustes.alarmaHumBaja}–${it.ajustes.alarmaHumAlta} %" },
                            ColoresEstado.humedad, Modifier.weight(1f))
                        Indicador(Icons.Rounded.Bolt, "Potencia", "${e.potencia} %",
                            if (u.configuracion?.ajustes?.modo == 'P') "PWM en las celdas" else "${e.potencia / 25} de 4 celdas",
                            ColoresEstado.potencia, Modifier.weight(1f), progreso = e.potencia / 100f)
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val maxCal = u.configuracion?.ajustes?.maxLadoCaliente
                        val cal = e.ladoCaliente
                        Indicador(Icons.Rounded.LocalFireDepartment, "Lado caliente", Formato.temperatura(cal),
                            maxCal?.let { "Límite $it °C" },
                            if (cal != null && maxCal != null && cal > maxCal - 10) ColoresEstado.alarma else ColoresEstado.aviso,
                            Modifier.weight(1f), progreso = if (cal != null && maxCal != null) (cal / maxCal).coerceIn(0f, 1f) else null)
                        Indicador(Icons.Rounded.SignalCellularAlt, "Señal GSM", Formato.calidadSenal(e.senal),
                            e.minutosEncendido?.let { "Encendido hace ${Formato.duracion(it)}" }, ColoresEstado.frio, Modifier.weight(1f),
                            progreso = e.senal?.let { it / 31f })
                    }
                }
                item { TarjetaReciente(u, irAHistorial) }
                item {
                    Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.PowerSettingsNew, null, tint = if (u.encendido) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Control de temperatura", style = MaterialTheme.typography.titleMedium)
                                Text(if (u.encendido) "Encendido: el equipo regula las celdas" else "Apagado: las celdas no enfrían",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = u.encendido, onCheckedChange = { confirmarEncendido = it })
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledTonalButton(onClick = vm::pedirLecturas, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Lecturas")
                        }
                        OutlinedButton(onClick = vm::descargarHistorial, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.CloudDownload, null); Spacer(Modifier.width(8.dp)); Text("Historial 48 h")
                        }
                    }
                }
            }
        }
    }

    confirmarEncendido?.let { encender ->
        AlertDialog(
            onDismissRequest = { confirmarEncendido = null },
            icon = { Icon(Icons.Rounded.PowerSettingsNew, null) },
            title = { Text(if (encender) "¿Encender el control?" else "¿Apagar el control?") },
            text = { Text(if (encender) "El equipo empezará a regular las celdas Peltier para llegar al objetivo." else "Las celdas se apagarán y el cuarto dejará de enfriarse. Las fallas de equipo se seguirán avisando.") },
            confirmButton = { TextButton(onClick = { vm.encender(encender); confirmarEncendido = null }) { Text(if (encender) "Encender" else "Apagar") } },
            dismissButton = { TextButton(onClick = { confirmarEncendido = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun SinDatos(pedir: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.AcUnit, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text("Aún no hay lecturas", style = MaterialTheme.typography.titleLarge)
            Text("Pide las lecturas al equipo. La respuesta llega por SMS en unos segundos.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Button(onClick = pedir) { Text("Pedir lecturas") }
        }
    }
}

@Composable
private fun TarjetaTemperatura(u: PanelUi, ahora: Long) {
    val e = u.estado!!.datos
    val enAlarma = e.alarmas and (Alarma.TEMP_ALTA or Alarma.TEMP_BAJA or Alarma.SENSOR) != 0
    val degradado = when {
        enAlarma -> listOf(Color(0xFFC62828), Color(0xFFE65100))
        !u.encendido -> listOf(Color(0xFF455A64), Color(0xFF607D8B))
        else -> listOf(Color(0xFF0B5CAD), Color(0xFF0097A7))
    }
    val tendencia = tendencia(u.recientes)
    val estadoTexto = when {
        e.bloqueoTermico -> "Bloqueo térmico: celdas apagadas"
        e.alarmas and Alarma.SOBRECALENTADO != 0 && e.potencia == 0 -> "Protección térmica: celdas apagadas"
        !u.encendido -> "Control apagado"
        e.potencia > 0 -> if (u.configuracion?.ajustes?.modo == 'P') "Enfriando al ${e.potencia} %" else "Enfriando · ${e.potencia / 25} de 4 celdas"
        else -> "En espera"
    }
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(degradado), RoundedCornerShape(28.dp))
            .padding(24.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.DeviceThermostat, null, tint = Color.White.copy(alpha = 0.9f))
                Spacer(Modifier.width(8.dp))
                Text("Temperatura", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                tendencia?.let { (icono, texto) ->
                    Icon(icono, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(texto, color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(Formato.numero(e.temperatura), color = Color.White, style = MaterialTheme.typography.displayLarge)
                Text(" °C", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 14.dp))
            }
            Text("Objetivo ${Formato.temperatura(e.objetivo)}" + (u.configuracion?.let {
                " · alarmas ${Formato.numero(it.ajustes.alarmaTempBaja)} a ${Formato.numero(it.ajustes.alarmaTempAlta)} °C"
            } ?: ""), color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Surface(color = Color.White.copy(alpha = 0.18f), contentColor = Color.White, shape = RoundedCornerShape(50)) {
                Text(estadoTexto, Modifier.padding(horizontal = 14.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Tendencia de la última hora a partir de las lecturas guardadas. */
private fun tendencia(lecturas: List<Lectura>): Pair<ImageVector, String>? {
    val conDato = lecturas.filter { it.temperatura != null }
    val ultima = conDato.lastOrNull() ?: return null
    val hace1h = conDato.lastOrNull { ultima.tiempo - it.tiempo >= 45 * 60_000 } ?: return null
    val cambio = ultima.temperatura!! - hace1h.temperatura!!
    return when {
        cambio > 0.3f -> Icons.Rounded.TrendingUp to "+%.1f°/h".format(cambio)
        cambio < -0.3f -> Icons.Rounded.TrendingDown to "%.1f°/h".format(cambio)
        else -> Icons.Rounded.TrendingFlat to "estable"
    }
}

@Composable
private fun TarjetaBloqueo(reanudar: () -> Unit) {
    var confirmar by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.onError)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.LocalFireDepartment, null)
                Spacer(Modifier.width(12.dp))
                Text("Celdas bloqueadas", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            Text("El disipador se sobrecalentó 3 veces en una hora. Lo más probable es que haya fallado su ventilador. " +
                "Revísalo antes de reanudar: las celdas no volverán a encender solas.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { confirmar = true }, colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.onError, MaterialTheme.colorScheme.error)) {
                Text("Ya lo revisé: reanudar")
            }
        }
    }
    if (confirmar) {
        AlertDialog(
            onDismissRequest = { confirmar = false },
            title = { Text("¿Reanudar el enfriamiento?") },
            text = { Text("Si el ventilador sigue sin funcionar, la protección volverá a apagar las celdas.") },
            confirmButton = { TextButton(onClick = { reanudar(); confirmar = false }) { Text("Reanudar") } },
            dismissButton = { TextButton(onClick = { confirmar = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun TarjetaAlarmas(alarmas: Int, reconocer: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.NotificationsActive, null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(12.dp))
                Text(if (Integer.bitCount(alarmas) == 1) "Alarma activa" else "${Integer.bitCount(alarmas)} alarmas activas",
                    style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            }
            Spacer(Modifier.height(8.dp))
            Alarma.nombres(alarmas).forEach {
                Text("• $it", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onErrorContainer)
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = reconocer, colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.error)) {
                Text("Reconocer (dejar de recordar)")
            }
        }
    }
}

@Composable
private fun Indicador(
    icono: ImageVector, titulo: String, valor: String, detalle: String?, color: Color,
    modifier: Modifier = Modifier, progreso: Float? = null,
) {
    Card(modifier, colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).background(color.copy(alpha = 0.15f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                    Icon(icono, null, tint = color, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(titulo, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            Text(valor, style = MaterialTheme.typography.headlineSmall)
            progreso?.let {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { it }, color = color, trackColor = color.copy(alpha = 0.15f), modifier = Modifier.fillMaxWidth())
            }
            detalle?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
        }
    }
}

@Composable
private fun TarjetaReciente(u: PanelUi, irAHistorial: () -> Unit) {
    val ahora = System.currentTimeMillis()
    val puntos = u.recientes.mapNotNull { l -> l.temperatura?.let { Punto(l.tiempo, it) } }
    Card(Modifier.fillMaxWidth().clickable(onClick = irAHistorial), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Últimas $HORAS_RECIENTES horas", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("Ver historial", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(8.dp))
            if (puntos.size < 2) {
                Text("Aparecerá cuando lleguen más lecturas o descargues el historial del equipo.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val a = u.configuracion?.ajustes
                Grafica(
                    series = listOf(Serie("Temperatura", puntos, MaterialTheme.colorScheme.primary, "°C")),
                    desde = ahora - HORAS_RECIENTES * 3_600_000L, hasta = ahora, alto = 140.dp,
                    rangoPermitido = a?.let { it.alarmaTempBaja..it.alarmaTempAlta }, referencia = a?.objetivo,
                )
            }
        }
    }
}
