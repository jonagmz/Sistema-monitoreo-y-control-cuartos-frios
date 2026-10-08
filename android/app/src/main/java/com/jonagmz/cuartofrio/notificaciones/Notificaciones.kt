package com.jonagmz.cuartofrio.notificaciones

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jonagmz.cuartofrio.MainActivity
import com.jonagmz.cuartofrio.R

class Notificaciones(private val context: Context) {
    private companion object {
        const val CANAL_ALARMAS = "alarmas"
        const val CANAL_AVISOS = "avisos"
        const val ID_ALARMA = 1
        const val ID_AVISO = 2
    }

    fun crearCanales() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CANAL_ALARMAS, "Alarmas", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Temperatura fuera de rango, fallas de sensores o de enfriamiento"
        })
        manager.createNotificationChannel(NotificationChannel(CANAL_AVISOS, "Avisos", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Cortes de luz y reinicios del equipo"
        })
    }

    fun alarma(equipo: String, titulo: String, texto: String) = mostrar(CANAL_ALARMAS, ID_ALARMA, "⚠ $equipo: $titulo", texto, true)
    fun aviso(equipo: String, texto: String) = mostrar(CANAL_AVISOS, ID_AVISO, equipo, texto, false)

    private fun mostrar(canal: String, id: Int, titulo: String, texto: String, alarma: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val abrir = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notificacion = NotificationCompat.Builder(context, canal)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setCategory(if (alarma) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_STATUS)
            .setPriority(if (alarma) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(abrir)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notificacion)
    }
}
