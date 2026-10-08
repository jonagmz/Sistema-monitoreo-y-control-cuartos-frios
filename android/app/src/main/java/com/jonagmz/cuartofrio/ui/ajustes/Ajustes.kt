@file:OptIn(ExperimentalMaterial3Api::class)

package com.jonagmz.cuartofrio.ui.ajustes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.jonagmz.cuartofrio.datos.Equipo
import com.jonagmz.cuartofrio.datos.Repositorio
import com.jonagmz.cuartofrio.protocolo.AjustesEquipo
import com.jonagmz.cuartofrio.ui.EstadoComando
import com.jonagmz.cuartofrio.ui.Formato
import com.jonagmz.cuartofrio.ui.ahoraQueAvanza
import com.jonagmz.cuartofrio.ui.vm
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class AjustesViewModel(private val repo: Repositorio) : ViewModel() {
    val configuracion = repo.configuracion.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val equipo = repo.equipo.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val pendiente = repo.pendiente
    val mensajes = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** Lo que se está editando; se reemplaza por lo que confirme el equipo si no hay cambios sin enviar. */
    var edicion by mutableStateOf(AjustesEquipo())
        private set
    // También es estado: si llega una configuración igual a la que se edita, edicion no cambia
    // y sin esto la pantalla no se enteraría de que ya no hay cambios pendientes.
    private var base by mutableStateOf<AjustesEquipo?>(null)

    init {
        viewModelScope.launch {
            repo.configuracion.collect { c ->
                if (c != null && (base == null || edicion == base)) edicion = c.ajustes
                base = c?.ajustes
            }
        }
    }

    fun cambiar(f: (AjustesEquipo) -> AjustesEquipo) { edicion = f(edicion) }
    fun descartar() { base?.let { edicion = it } }
    /** Campos distintos de lo que tiene el equipo (o de los valores de fábrica si aún no se ha leído). */
    fun cambios(): Int {
        val antes = (base ?: AjustesEquipo()).comoCampos()
        return edicion.comoCampos().count { (k, v) -> antes[k] != v }
    }

    fun enviar() = viewModelScope.launch {
        when (val r = repo.enviarAjustes(edicion)) {
            is Repositorio.Resultado.Error -> mensajes.emit(r.mensaje)
            Repositorio.Resultado.SinCambios -> mensajes.emit("No hay cambios que enviar")
            Repositorio.Resultado.Enviado -> mensajes.emit("Enviado. El equipo confirmará con un SMS.")
        }
    }

    fun leerDelEquipo() = viewModelScope.launch {
        (repo.pedirConfiguracion() as? Repositorio.Resultado.Error)?.let { mensajes.emit(it.mensaje) }
    }

    fun guardarEquipo(e: Equipo) = viewModelScope.launch {
        repo.guardarEquipo(e)
        mensajes.emit("Equipo guardado")
    }

    fun borrarDatos() = viewModelScope.launch {
        repo.borrarDatosLocales()
        mensajes.emit("Historial y eventos borrados de este teléfono")
    }
}

private val INTERVALOS_REPORTE = listOf(0, 15, 30, 60, 120, 180, 360, 720, 1440)

@Composable
fun PantallaAjustes() {
    val vm = vm { AjustesViewModel(it) }
    val config by vm.configuracion.collectAsStateWithLifecycle()
    val equipo by vm.equipo.collectAsStateWithLifecycle()
    val pendiente by vm.pendiente.collectAsStateWithLifecycle(null)
    val avisos = remember { SnackbarHostState() }
    val ahora = ahoraQueAvanza()
    var pidAbierto by rememberSaveable { mutableStateOf(false) }
    var confirmarBorrado by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.mensajes.collect { avisos.showSnackbar(it) } }
    val a = vm.edicion
    val errores = a.validar()
    val cambios = vm.cambios()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                actions = { IconButton(onClick = vm::leerDelEquipo) { Icon(Icons.Rounded.Sync, "Leer del equipo") } },
            )
        },
        bottomBar = {
            AnimatedVisibility(cambios > 0 || errores.isNotEmpty()) {
                Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (errores.isEmpty()) "$cambios ${if (cambios == 1) "cambio" else "cambios"} sin enviar" else errores.first(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (errores.isEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
                            if (config != null && errores.isEmpty()) TextButton(onClick = vm::descartar, contentPadding = PaddingValues(0.dp)) { Text("Descartar") }
                        }
                        Button(onClick = vm::enviar, enabled = errores.isEmpty() && cambios > 0) { Text("Enviar al equipo") }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(avisos) },
        contentWindowInsets = WindowInsets(0),
    ) { relleno ->
        LazyColumn(Modifier.padding(relleno), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { EstadoComando(pendiente, ahora, Modifier.fillMaxWidth()) }
            item {
                Text(
                    config?.let { "Configuración confirmada por el equipo ${Formato.relativo(it.tiempo, ahora)}" }
                        ?: "Aún no se ha leído la configuración del equipo. Pulsa ⟳ para leerla antes de cambiarla.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Seccion("Temperatura") {
                    Paso("Objetivo", "${Formato.numero(a.objetivo)} °C", "Temperatura que el PID intenta mantener",
                        { vm.cambiar { it.copy(objetivo = it.objetivo - 0.5f) } }, { vm.cambiar { it.copy(objetivo = it.objetivo + 0.5f) } })
                    Paso("Alarma baja", "${Formato.numero(a.alarmaTempBaja)} °C", null,
                        { vm.cambiar { it.copy(alarmaTempBaja = it.alarmaTempBaja - 0.5f) } }, { vm.cambiar { it.copy(alarmaTempBaja = it.alarmaTempBaja + 0.5f) } })
                    Paso("Alarma alta", "${Formato.numero(a.alarmaTempAlta)} °C", null,
                        { vm.cambiar { it.copy(alarmaTempAlta = it.alarmaTempAlta - 0.5f) } }, { vm.cambiar { it.copy(alarmaTempAlta = it.alarmaTempAlta + 0.5f) } })
                    Paso("Retardo de alarmas", "${a.retardoAlarmaMin} min", "Evita alarmas al abrir la puerta",
                        { vm.cambiar { it.copy(retardoAlarmaMin = (it.retardoAlarmaMin - 1).coerceAtLeast(0)) } }, { vm.cambiar { it.copy(retardoAlarmaMin = it.retardoAlarmaMin + 1) } })
                    FilaInterruptor("Control encendido", a.encendido) { v -> vm.cambiar { it.copy(encendido = v) } }
                }
            }
            item {
                Seccion("Humedad") {
                    Paso("Alarma baja", "${a.alarmaHumBaja} %", null,
                        { vm.cambiar { it.copy(alarmaHumBaja = it.alarmaHumBaja - 1) } }, { vm.cambiar { it.copy(alarmaHumBaja = it.alarmaHumBaja + 1) } })
                    Paso("Alarma alta", "${a.alarmaHumAlta} %", null,
                        { vm.cambiar { it.copy(alarmaHumAlta = it.alarmaHumAlta - 1) } }, { vm.cambiar { it.copy(alarmaHumAlta = it.alarmaHumAlta + 1) } })
                }
            }
            item {
                Seccion("Celdas Peltier") {
                    Paso("Potencia máxima", "${a.potenciaMax} %", "Limita el consumo y el calor en el disipador",
                        { vm.cambiar { it.copy(potenciaMax = (it.potenciaMax - 5).coerceAtLeast(0)) } }, { vm.cambiar { it.copy(potenciaMax = (it.potenciaMax + 5).coerceAtMost(100)) } })
                    Paso("Límite del lado caliente", "${a.maxLadoCaliente} °C", "Reduce potencia 10 °C antes y apaga al llegar",
                        { vm.cambiar { it.copy(maxLadoCaliente = it.maxLadoCaliente - 1) } }, { vm.cambiar { it.copy(maxLadoCaliente = it.maxLadoCaliente + 1) } })
                }
            }
            item {
                Seccion("Avisos y registro") {
                    val i = INTERVALOS_REPORTE.indexOf(a.reporteMin)
                    Paso("Reporte periódico", if (a.reporteMin == 0) "No" else Formato.duracion(a.reporteMin.toLong()), "Cada SMS puede tener costo",
                        { vm.cambiar { it.copy(reporteMin = INTERVALOS_REPORTE[(if (i < 0) 3 else i - 1).coerceAtLeast(0)]) } },
                        { vm.cambiar { it.copy(reporteMin = INTERVALOS_REPORTE[(if (i < 0) 3 else i + 1).coerceAtMost(INTERVALOS_REPORTE.lastIndex)]) } })
                    Paso("Recordar alarmas cada", if (a.recordatorioH == 0) "No" else "${a.recordatorioH} h", "Mientras nadie las reconozca",
                        { vm.cambiar { it.copy(recordatorioH = (it.recordatorioH - 1).coerceAtLeast(0)) } }, { vm.cambiar { it.copy(recordatorioH = (it.recordatorioH + 1).coerceAtMost(48)) } })
                    Paso("Muestra del historial cada", "${a.historialMin} min", "Con 15 min el equipo guarda 48 h",
                        { vm.cambiar { it.copy(historialMin = (it.historialMin - 5).coerceAtLeast(5)) } }, { vm.cambiar { it.copy(historialMin = (it.historialMin + 5).coerceAtMost(60)) } })
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("PID (avanzado)", style = MaterialTheme.typography.titleMedium)
                                Text("KP ${fmt(a.kp)} · KI ${fmt(a.ki)} · KD ${fmt(a.kd)}", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { pidAbierto = !pidAbierto }) {
                                Icon(if (pidAbierto) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, "Mostrar PID")
                            }
                        }
                        AnimatedVisibility(pidAbierto) {
                            Column {
                                Text("Más KP responde más fuerte; más KI corrige el error que queda; KD frena los cambios bruscos. Cámbialos poco a poco.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Paso("KP", fmt(a.kp), "% de potencia por °C",
                                    { vm.cambiar { it.copy(kp = (it.kp - 1).coerceAtLeast(0f)) } }, { vm.cambiar { it.copy(kp = it.kp + 1) } })
                                Paso("KI", fmt(a.ki), "% por °C y minuto",
                                    { vm.cambiar { it.copy(ki = ((it.ki - 0.1f) * 10).roundToInt().coerceAtLeast(0) / 10f) } },
                                    { vm.cambiar { it.copy(ki = ((it.ki + 0.1f) * 10).roundToInt() / 10f) } })
                                Paso("KD", fmt(a.kd), "% por °C/min",
                                    { vm.cambiar { it.copy(kd = (it.kd - 1).coerceAtLeast(0f)) } }, { vm.cambiar { it.copy(kd = it.kd + 1) } })
                            }
                        }
                    }
                }
            }
            item { equipo?.let { DatosEquipo(it, vm::guardarEquipo) } }
            item {
                OutlinedButton(onClick = { confirmarBorrado = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Borrar historial y eventos de este teléfono")
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (confirmarBorrado) {
        AlertDialog(
            onDismissRequest = { confirmarBorrado = false },
            title = { Text("¿Borrar datos locales?") },
            text = { Text("Se borran las lecturas y eventos guardados en el teléfono. El equipo conserva sus últimas 48 h.") },
            confirmButton = { TextButton(onClick = { vm.borrarDatos(); confirmarBorrado = false }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { confirmarBorrado = false }) { Text("Cancelar") } },
        )
    }
}

private fun fmt(v: Float) = if (v == v.roundToInt().toFloat()) "${v.roundToInt()}" else "%.1f".format(v)

@Composable
private fun Seccion(titulo: String, contenido: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            contenido()
        }
    }
}

@Composable
private fun Paso(titulo: String, valor: String, ayuda: String?, menos: () -> Unit, mas: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.bodyLarge)
            ayuda?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        FilledTonalIconButton(onClick = menos) { Icon(Icons.Rounded.Remove, "Menos") }
        Text(valor, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 76.dp))
        FilledTonalIconButton(onClick = mas) { Icon(Icons.Rounded.Add, "Más") }
    }
}

@Composable
private fun FilaInterruptor(titulo: String, valor: Boolean, cambiar: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(titulo, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = valor, onCheckedChange = cambiar)
    }
}

@Composable
fun DatosEquipo(actual: Equipo, guardar: (Equipo) -> Unit) {
    var nombre by rememberSaveable(actual) { mutableStateOf(actual.nombre) }
    var numero by rememberSaveable(actual) { mutableStateOf(actual.numero) }
    var pin by rememberSaveable(actual) { mutableStateOf(actual.pin) }
    val valido = numero.filter(Char::isDigit).length >= 10
    Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Equipo", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(nombre, { nombre = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(numero, { numero = it }, label = { Text("Número del chip del equipo") }, singleLine = true,
                isError = numero.isNotEmpty() && !valido, supportingText = { Text("Solo se aceptan SMS de este número") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(pin, { pin = it.filter(Char::isLetterOrDigit).take(8) }, label = { Text("PIN del equipo") }, singleLine = true,
                supportingText = { Text("El mismo que PIN_SEGURIDAD en config.h") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
            HorizontalDivider()
            Row {
                Spacer(Modifier.weight(1f))
                Button(onClick = { guardar(Equipo(nombre, numero, pin)) }, enabled = valido && (nombre != actual.nombre || numero != actual.numero || pin != actual.pin)) {
                    Text("Guardar")
                }
            }
        }
    }
}
