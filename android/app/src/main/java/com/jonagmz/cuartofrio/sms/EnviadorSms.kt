package com.jonagmz.cuartofrio.sms

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat

class EnviadorSms(private val context: Context) {

    /** Envía los textos en orden. Devuelve un mensaje de error o null si se entregaron al sistema. */
    fun enviar(numero: String, textos: List<String>): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            return "Falta el permiso para enviar SMS"
        }
        val manager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION") SmsManager.getDefault()
        }
        return try {
            textos.forEachIndexed { i, texto ->
                val resultado = PendingIntent.getBroadcast(
                    context, i, Intent(context, ResultadoEnvio::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                manager.sendTextMessage(numero, null, texto, resultado, null)
            }
            null
        } catch (e: RuntimeException) {
            "No se pudo enviar el SMS: ${e.message ?: "error desconocido"}"
        }
    }
}
