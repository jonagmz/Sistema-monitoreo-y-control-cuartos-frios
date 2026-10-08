@file:OptIn(ExperimentalMaterial3Api::class)

package com.jonagmz.cuartofrio.ui.eventos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.EventNote
import androidx.compose.material.icons.rounded.PowerOff
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jonagmz.cuartofrio.datos.Evento
import com.jonagmz.cuartofrio.datos.Repositorio
import com.jonagmz.cuartofrio.ui.Formato
import com.jonagmz.cuartofrio.ui.tema.ColoresEstado
import com.jonagmz.cuartofrio.ui.vm

class EventosViewModel(repo: Repositorio) : ViewModel() {
    val eventos = repo.eventos
}

@Composable
fun PantallaEventos() {
    val vm = vm { EventosViewModel(it) }
    val eventos by vm.eventos.collectAsStateWithLifecycle(emptyList())
    Scaffold(topBar = { TopAppBar(title = { Text("Eventos") }) }, contentWindowInsets = WindowInsets(0)) { relleno ->
        if (eventos.isEmpty()) {
            Column(Modifier.padding(relleno).fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(48.dp))
                Icon(Icons.Rounded.EventNote, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(12.dp))
                Text("Aquí aparecerán las alarmas, cortes de luz y cambios de configuración.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        val porDia = eventos.groupBy { Formato.dia(it.tiempo) }
        LazyColumn(Modifier.padding(relleno), contentPadding = PaddingValues(bottom = 16.dp)) {
            porDia.forEach { (dia, delDia) ->
                item(key = "dia-$dia") {
                    Text(dia, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
                }
                items(delDia, key = { it.id }) { FilaEvento(it) }
            }
        }
    }
}

@Composable
private fun FilaEvento(e: Evento) {
    val (icono, color) = when (e.tipo) {
        Evento.ALARMA -> Icons.Rounded.Warning to ColoresEstado.alarma
        Evento.ALARMA_RESUELTA -> Icons.Rounded.CheckCircle to ColoresEstado.ok
        Evento.INICIO -> Icons.Rounded.PowerOff to ColoresEstado.aviso
        Evento.CONFIGURACION -> Icons.Rounded.Tune to ColoresEstado.frio
        Evento.ERROR -> Icons.Rounded.ErrorOutline to ColoresEstado.alarma
        else -> Icons.Rounded.Send to Color.Gray
    }
    ListItem(
        leadingContent = {
            Box(Modifier.size(40.dp).background(color.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icono, null, tint = color, modifier = Modifier.size(22.dp))
            }
        },
        headlineContent = { Text(e.titulo) },
        supportingContent = e.detalle.takeIf { it.isNotBlank() }?.let { { Text(it) } },
        trailingContent = { Text(Formato.horaDe(e.tiempo), style = MaterialTheme.typography.labelMedium) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
