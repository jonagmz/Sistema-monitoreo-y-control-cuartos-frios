package com.jonagmz.cuartofrio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jonagmz.cuartofrio.ui.AppCuartoFrio
import com.jonagmz.cuartofrio.ui.tema.TemaCuartoFrio

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { TemaCuartoFrio { AppCuartoFrio() } }
    }
}
