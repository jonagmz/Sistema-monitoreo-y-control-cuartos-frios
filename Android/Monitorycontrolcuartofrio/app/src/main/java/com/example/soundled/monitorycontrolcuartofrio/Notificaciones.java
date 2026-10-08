package com.example.soundled.monitorycontrolcuartofrio;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

final class Notificaciones {

    private static final String CANAL_ALARMAS = "alarmas";
    private static final int ID_ALARMA = 1;

    private Notificaciones() {
    }

    static void crearCanal(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel canal = new NotificationChannel(CANAL_ALARMAS,
                    context.getString(R.string.canal_alarmas), NotificationManager.IMPORTANCE_HIGH);
            context.getSystemService(NotificationManager.class).createNotificationChannel(canal);
        }
    }

    static void mostrarAlarma(Context context, Reporte reporte) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        crearCanal(context);
        PendingIntent abrir = PendingIntent.getActivity(context, 0,
                new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder aviso = new NotificationCompat.Builder(context, CANAL_ALARMAS)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle(context.getString(R.string.alarma_en, reporte.ubicacion))
                .setContentText(describirAlarmas(context, reporte))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setContentIntent(abrir)
                .setAutoCancel(true);
        NotificationManagerCompat.from(context).notify(ID_ALARMA, aviso.build());
    }

    static String describirAlarmas(Context context, Reporte r) {
        StringBuilder texto = new StringBuilder();
        if (r.errorSensor) agregar(texto, context.getString(R.string.alarma_sensor));
        if (r.alarmaTempAlta) agregar(texto, context.getString(R.string.alarma_temp_alta, r.temperatura));
        if (r.alarmaTempBaja) agregar(texto, context.getString(R.string.alarma_temp_baja, r.temperatura));
        if (r.alarmaHumAlta) agregar(texto, context.getString(R.string.alarma_hum_alta, r.humedad));
        if (r.alarmaHumBaja) agregar(texto, context.getString(R.string.alarma_hum_baja, r.humedad));
        return texto.toString();
    }

    private static void agregar(StringBuilder texto, String parte) {
        if (texto.length() > 0) texto.append(" · ");
        texto.append(parte);
    }
}
