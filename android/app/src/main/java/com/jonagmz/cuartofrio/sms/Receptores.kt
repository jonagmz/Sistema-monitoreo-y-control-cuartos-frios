package com.jonagmz.cuartofrio.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.jonagmz.cuartofrio.CuartoFrioApp
import kotlinx.coroutines.launch

/** SMS entrantes: funciona aunque la app esté cerrada. Solo el sistema puede enviarlo (permiso BROADCAST_SMS). */
class ReceptorSms : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val partes = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (partes.isEmpty()) return
        val numero = partes[0].originatingAddress
        val texto = partes.joinToString("") { it.messageBody.orEmpty() }
        val hora = partes[0].timestampMillis.takeIf { it > 0 } ?: System.currentTimeMillis()
        val contenedor = (context.applicationContext as CuartoFrioApp).contenedor
        val pendiente = goAsync()
        contenedor.alcance.launch {
            try {
                contenedor.repositorio.procesarSms(numero, texto, hora)
            } finally {
                pendiente.finish()
            }
        }
    }
}

/** Resultado del envío de un SMS (lo entrega el sistema al terminar de enviarlo). */
class ResultadoEnvio : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val exito = resultCode == Activity.RESULT_OK
        val contenedor = (context.applicationContext as CuartoFrioApp).contenedor
        val pendiente = goAsync()
        contenedor.alcance.launch {
            try {
                contenedor.repositorio.resultadoEnvio(exito)
            } finally {
                pendiente.finish()
            }
        }
    }
}
