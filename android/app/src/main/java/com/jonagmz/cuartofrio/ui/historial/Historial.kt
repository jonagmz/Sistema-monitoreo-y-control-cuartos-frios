@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalCoroutinesApi::class)

package com.jonagmz.cuartofrio.ui.historial

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.jonagmz.cuartofrio.datos.ConfiguracionEquipo
import com.jonagmz.cuartofrio.datos.EstadoEquipo
import com.jonagmz.cuartofrio.datos.Lectura
import com.jonagmz.cuartofrio.datos.Repositorio
import com.jonagmz.cuartofrio.ui.EstadoComando
import com.jonagmz.cuartofrio.ui.Formato
import com.jonagmz.cuartofrio.ui.ahoraQueAvanza
import com.jonagmz.cuartofrio.ui.componentes.Grafica
import com.jonagmz.cuartofrio.ui.componentes.Punto
import com.jonagmz.cuartofrio.ui.componentes.Serie
import com.jonagmz.cuartofrio.ui.tema.ColoresEstado
import com.jonagmz.cuartofrio.ui.vm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class Rango(val etiqueta: String, val horas: Int) {
    H6("6 h", 6), H24("24 h", 24), H48("48 h", 48), D7("7 d", 168), D30("30 d", 720)
}

data class Estadisticas(val minimo: Float, val maximo: Float, val promedio: Float, val enRango: Int?, val muestras: Int)

data class HistorialUi(
    val rango: Rango,
    val desde: Long,
    val hasta: Long,
    val lecturas: List<Lectura>,
    val configuracion: ConfiguracionEquipo?,
    val estado: EstadoEquipo?,
) {
    val estadisticas: Estadisticas? = lecturas.mapNotNull { it.temperatura }.takeIf { it.isNotEmpty() }?.let { t ->
        val a = configuracion?.ajustes
        Estadisticas(t.min(), t.max(), t.average().toFloat(),
            a?.let { c -> t.count { it in c.alarmaTempBaja..c.alarmaTempAlta } * 100 / t.size }, t.size)
    }
}

class HistorialViewModel(private val repo: Repositorio) : ViewModel() {
    private val rango = MutableStateFlow(Rango.H24)
    val mensajes = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val pendiente = repo.pendiente

    val ui: StateFlow<HistorialUi?> = rango.flatMapLatest { r ->
        val hasta = System.currentTimeMillis()
        val desde = hasta - r.horas * 3_600_000L
        combine(repo.lecturasDesde(desde), repo.configuracion, repo.estado) { l, c, e -> HistorialUi(r, desde, hasta, l, c, e) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun elegir(r: Rango) { rango.value = r }

    fun descargar(horas: Int) = viewModelScope.launch {
        (repo.descargarHistorial(horas) as? Repositorio.Resultado.Error)?.let { mensajes.emit(it.mensaje) }
    }

    suspend fun exportar(context: Context, destino: Uri): Int = withContext(Dispatchers.IO) {
        val lecturas = repo.todasLasLecturas()
        val formato = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
        context.contentResolver.openOutputStream(destino)?.bufferedWriter()?.use { w ->
            w.write("fecha,temperatura_c,humedad_pct,lado_caliente_c,potencia_pct,alarma,origen\n")
            lecturas.forEach { l ->
                w.write(listOf(
                    formato.format(Instant.ofEpochMilli(l.tiempo)), l.temperatura?.toString().orEmpty(), l.humedad?.toString().orEmpty(),
                    l.ladoCaliente?.toString().orEmpty(), l.potencia?.toString().orEmpty(), if (l.alarmas != 0) "1" else "0",
                    if (l.origen == Lectura.REPORTE) "reporte" else "historial",
                ).joinToString(",") + "\n")
            }
        }
        lecturas.size
    }
}

@Composable
fun PantallaHistorial() {
    val vm = vm { HistorialViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val pendiente by vm.pendiente.collectAsStateWithLifecycle(null)
    val avisos = remember { SnackbarHostState() }
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    val ahora = ahoraQueAvanza()
    LaunchedEffect(Unit) { vm.mensajes.collect { avisos.showSnackbar(it) } }
    val exportar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) alcance.launch { avisos.showSnackbar("Exportadas ${vm.exportar(contexto, uri)} lecturas") }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historial") },
                actions = {
                    IconButton(onClick = { vm.descargar(48) }) { Icon(Icons.Rounded.CloudDownload, "Descargar del equipo") }
                    IconButton(onClick = { exportar.launch("cuarto-frio.csv") }) { Icon(Icons.Rounded.FileDownload, "Exportar CSV") }
                },
            )
        },
        snackbarHost = { SnackbarHost(avisos) },
        contentWindowInsets = WindowInsets(0),
    ) { relleno ->
        val u = ui ?: return@Scaffold
        LazyColumn(Modifier.padding(relleno), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Rango.entries.forEachIndexed { i, r ->
                        SegmentedButton(selected = u.rango == r, onClick = { vm.elegir(r) },
                            shape = SegmentedButtonDefaults.itemShape(i, Rango.entries.size)) { Text(r.etiqueta) }
                    }
                }
            }
            item { EstadoComando(pendiente, ahora, Modifier.fillMaxWidth()) }
            if (u.lecturas.isEmpty()) {
                item { SinHistorial { vm.descargar(48) } }
                return@LazyColumn
            }
            u.estadisticas?.let { e -> item { TarjetaEstadisticas(e) } }
            val a = u.configuracion?.ajustes
            val hueco = ((a?.historialMin ?: 15) * 3 + 5) * 60_000L
            item {
                TarjetaGrafica("Temperatura", "Banda verde: rango sin alarma · línea punteada: objetivo") {
                    Grafica(
                        listOf(Serie("Temp.", u.lecturas.mapNotNull { l -> l.temperatura?.let { Punto(l.tiempo, it) } }, MaterialTheme.colorScheme.primary, "°C")),
                        u.desde, u.hasta, rangoPermitido = a?.let { it.alarmaTempBaja..it.alarmaTempAlta }, referencia = a?.objetivo,
                        huecoMaxMs = hueco,
                    )
                }
            }
            item {
                TarjetaGrafica("Humedad", null) {
                    Grafica(
                        listOf(Serie("Humedad", u.lecturas.mapNotNull { l -> l.humedad?.let { Punto(l.tiempo, it) } }, ColoresEstado.humedad, "%")),
                        u.desde, u.hasta, rangoPermitido = a?.let { it.alarmaHumBaja.toFloat()..it.alarmaHumAlta.toFloat() },
                        huecoMaxMs = hueco, decimales = 0,
                    )
                }
            }
            item {
                TarjetaGrafica("Celdas Peltier", "Potencia aplicada y temperatura del lado caliente") {
                    Grafica(
                        listOf(
                            Serie("Potencia", u.lecturas.mapNotNull { l -> l.potencia?.let { Punto(l.tiempo, it.toFloat()) } }, ColoresEstado.potencia, "%"),
                            Serie("Lado caliente", u.lecturas.mapNotNull { l -> l.ladoCaliente?.let { Punto(l.tiempo, it) } }, ColoresEstado.aviso, "°C"),
                        ),
                        u.desde, u.hasta, yMinimo = 0f, huecoMaxMs = hueco, decimales = 0,
                    )
                }
            }
        }
    }
}

@Composable
private fun TarjetaEstadisticas(e: Estadisticas) {
    Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Dato("Mínima", "%.1f°".format(e.minimo))
            Dato("Máxima", "%.1f°".format(e.maximo))
            Dato("Promedio", "%.1f°".format(e.promedio))
            Dato("En rango", e.enRango?.let { "$it %" } ?: "--")
        }
    }
}

@Composable
private fun Dato(titulo: String, valor: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(valor, style = MaterialTheme.typography.titleLarge)
        Text(titulo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TarjetaGrafica(titulo: String, nota: String?, contenido: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium)
            nota?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.height(12.dp))
            contenido()
        }
    }
}

@Composable
private fun SinHistorial(descargar: () -> Unit) {
    Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.ShowChart, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text("Sin datos en este periodo", style = MaterialTheme.typography.titleMedium)
            Text("El equipo guarda las últimas 48 horas. Descárgalas (son unos 8 SMS).",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = descargar) { Text("Descargar historial del equipo") }
        }
    }
}
