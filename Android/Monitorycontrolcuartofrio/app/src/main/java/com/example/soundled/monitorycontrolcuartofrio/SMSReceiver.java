package com.example.soundled.monitorycontrolcuartofrio;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

/**
 * Recibe los SMS del equipo aunque la app esté cerrada: guarda el reporte, avisa si hay alarma y
 * actualiza la pantalla si está abierta. Los SMS de otros números se ignoran.
 */
public class SMSReceiver extends BroadcastReceiver {

    public static final String ACCION_REPORTE = "com.example.soundled.monitorycontrolcuartofrio.REPORTE";
    public static final String EXTRA_ERROR = "error";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            return;
        }
        SmsMessage[] partes = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (partes == null || partes.length == 0) {
            return;
        }
        Preferencias prefs = new Preferencias(context);
        if (!Comandos.mismoNumero(partes[0].getOriginatingAddress(), prefs.numeroEquipo())) {
            return;
        }
        StringBuilder texto = new StringBuilder();
        for (SmsMessage parte : partes) {
            texto.append(parte.getMessageBody());
        }

        Intent aviso = new Intent(ACCION_REPORTE).setPackage(context.getPackageName());
        Reporte reporte = Reporte.leer(texto.toString());
        if (reporte != null) {
            prefs.guardarReporte(texto.toString().trim(), System.currentTimeMillis());
            if (reporte.hayAlarma()) {
                Notificaciones.mostrarAlarma(context, reporte);
            }
        } else if (texto.toString().startsWith("ERROR")) {
            aviso.putExtra(EXTRA_ERROR, texto.toString());
        } else {
            return;
        }
        context.sendBroadcast(aviso);
    }
}
