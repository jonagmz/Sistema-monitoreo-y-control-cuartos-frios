package com.jonagmz.cuartofrio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jonagmz.cuartofrio.CuartoFrioApp
import com.jonagmz.cuartofrio.datos.Equipo
import kotlinx.coroutines.launch

/** Primer uso: datos del equipo. Al guardar se piden el estado y la configuración. */
@Composable
fun Bienvenida() {
    // El alcance de la app y no el de esta pantalla: al guardar el número la pantalla desaparece
    // y un alcance ligado a ella cancelaría el envío de los SMS.
    val contenedor = (LocalContext.current.applicationContext as CuartoFrioApp).contenedor
    val repo = contenedor.repositorio
    var nombre by rememberSaveable { mutableStateOf("Cuarto frío") }
    var numero by rememberSaveable { mutableStateOf("") }
    var pin by rememberSaveable { mutableStateOf("") }
    val valido = numero.filter(Char::isDigit).length >= 10

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier.size(72.dp).background(Brush.linearGradient(listOf(Color(0xFF0B5CAD), Color(0xFF0097A7))), RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.AcUnit, null, tint = Color.White, modifier = Modifier.size(40.dp)) }
        Text("Monitorea tu cuarto frío", style = MaterialTheme.typography.headlineMedium)
        Text("La app se comunica con el equipo por SMS: no necesita internet. Solo hace caso a los mensajes del número que escribas aquí.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(nombre, { nombre = it }, label = { Text("Nombre del equipo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(numero, { numero = it }, label = { Text("Número del chip del equipo") }, singleLine = true,
            placeholder = { Text("+52 667 000 0000") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth())
        OutlinedTextField(pin, { pin = it.filter(Char::isLetterOrDigit).take(8) }, label = { Text("PIN del equipo") }, singleLine = true,
            supportingText = { Text("Definido en config.h del firmware (PIN_SEGURIDAD)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
        Button(
            onClick = {
                contenedor.alcance.launch {
                    repo.guardarEquipo(Equipo(nombre, numero, pin))
                    repo.pedirConfiguracion()
                    repo.pedirEstado()
                }
            },
            enabled = valido, modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Conectar con el equipo") }
        Text("Se enviarán 2 SMS al equipo (configuración y lecturas).", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
