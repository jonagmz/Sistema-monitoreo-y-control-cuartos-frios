package com.jonagmz.cuartofrio.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.rounded.EventNote
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jonagmz.cuartofrio.CuartoFrioApp
import com.jonagmz.cuartofrio.ui.ajustes.PantallaAjustes
import com.jonagmz.cuartofrio.ui.eventos.PantallaEventos
import com.jonagmz.cuartofrio.ui.historial.PantallaHistorial
import com.jonagmz.cuartofrio.ui.panel.PantallaPanel

private enum class Destino(val ruta: String, val titulo: String, val icono: ImageVector, val iconoActivo: ImageVector) {
    PANEL("panel", "Panel", Icons.Outlined.Thermostat, Icons.Rounded.Thermostat),
    HISTORIAL("historial", "Historial", Icons.Outlined.ShowChart, Icons.Rounded.ShowChart),
    EVENTOS("eventos", "Eventos", Icons.Outlined.EventNote, Icons.Rounded.EventNote),
    AJUSTES("ajustes", "Ajustes", Icons.Outlined.Settings, Icons.Rounded.Settings),
}

@Composable
fun AppCuartoFrio() {
    val repo = (LocalContext.current.applicationContext as CuartoFrioApp).contenedor.repositorio
    val equipo by repo.equipo.collectAsStateWithLifecycle(null)
    val estado by repo.estado.collectAsStateWithLifecycle(null)

    val permisos = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    LaunchedEffect(Unit) {
        permisos.launch(buildList {
            add(Manifest.permission.SEND_SMS)
            add(Manifest.permission.RECEIVE_SMS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray())
    }

    val e = equipo ?: return
    if (!e.configurado) {
        Bienvenida()
        return
    }

    val nav = rememberNavController()
    val actual by nav.currentBackStackEntryAsState()
    // Cada pantalla tiene su barra superior y maneja la barra de estado; aquí solo se reserva la barra de navegación.
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            NavigationBar {
                Destino.entries.forEach { d ->
                    val seleccionado = actual?.destination?.route == d.ruta
                    NavigationBarItem(
                        selected = seleccionado,
                        onClick = {
                            nav.navigate(d.ruta) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            BadgedBox(badge = { if (d == Destino.PANEL && (estado?.datos?.alarmas ?: 0) != 0) Badge() }) {
                                Icon(if (seleccionado) d.iconoActivo else d.icono, null)
                            }
                        },
                        label = { Text(d.titulo) },
                    )
                }
            }
        },
    ) { relleno ->
        NavHost(nav, startDestination = Destino.PANEL.ruta, modifier = Modifier.padding(relleno)) {
            composable(Destino.PANEL.ruta) { PantallaPanel(irAHistorial = { nav.navigate(Destino.HISTORIAL.ruta) { launchSingleTop = true } }) }
            composable(Destino.HISTORIAL.ruta) { PantallaHistorial() }
            composable(Destino.EVENTOS.ruta) { PantallaEventos() }
            composable(Destino.AJUSTES.ruta) { PantallaAjustes() }
        }
    }
}
