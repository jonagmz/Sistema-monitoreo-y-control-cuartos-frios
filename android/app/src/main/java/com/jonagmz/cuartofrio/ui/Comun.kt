package com.jonagmz.cuartofrio.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jonagmz.cuartofrio.CuartoFrioApp
import com.jonagmz.cuartofrio.datos.Pendiente
import com.jonagmz.cuartofrio.datos.Repositorio

/** ViewModel con acceso al repositorio de la app. */
@Composable
inline fun <reified VM : ViewModel> vm(crossinline crear: (Repositorio) -> VM): VM {
    val repo = (LocalContext.current.applicationContext as CuartoFrioApp).contenedor.repositorio
    return viewModel { crear(repo) }
}

/** Banda que muestra el estado del último comando enviado al equipo. */
@Composable
fun EstadoComando(p: Pendiente?, ahora: Long, modifier: Modifier = Modifier) {
    if (p == null || ahora - p.tiempo > 15 * 60_000 && p.estado != Pendiente.ERROR) return
    if (p.estado == Pendiente.CONFIRMADO && ahora - p.tiempo > 60_000) return
    val (fondo, texto) = when (p.estado) {
        Pendiente.ERROR -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        Pendiente.CONFIRMADO -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurface
    }
    Surface(color = fondo, contentColor = texto, shape = MaterialTheme.shapes.large, modifier = modifier) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            when (p.estado) {
                Pendiente.ERROR -> Icon(Icons.Rounded.ErrorOutline, null)
                Pendiente.CONFIRMADO -> Icon(Icons.Rounded.CheckCircle, null)
                else -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    when (p.estado) {
                        Pendiente.ENVIANDO -> "Enviando SMS…"
                        Pendiente.ENVIADO -> "Esperando respuesta del equipo…"
                        Pendiente.CONFIRMADO -> "El equipo respondió"
                        else -> p.descripcion
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
                if (p.estado != Pendiente.ERROR) {
                    Text("${p.descripcion} · ${Formato.relativo(p.tiempo, ahora)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
