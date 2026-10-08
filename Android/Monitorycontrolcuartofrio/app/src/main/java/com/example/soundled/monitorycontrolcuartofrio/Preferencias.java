package com.example.soundled.monitorycontrolcuartofrio;

import android.content.Context;
import android.content.SharedPreferences;

/** Número del equipo y último reporte recibido, para mostrarlo aunque la app se haya cerrado. */
public final class Preferencias {

    private static final String ARCHIVO = "cuarto_frio";
    private static final String NUMERO_EQUIPO = "numero_equipo";
    private static final String ULTIMO_REPORTE = "ultimo_reporte";
    private static final String HORA_REPORTE = "hora_reporte";

    private final SharedPreferences prefs;

    public Preferencias(Context context) {
        prefs = context.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE);
    }

    public String numeroEquipo() {
        return prefs.getString(NUMERO_EQUIPO, "");
    }

    public void guardarNumeroEquipo(String numero) {
        prefs.edit().putString(NUMERO_EQUIPO, numero.trim()).apply();
    }

    public void guardarReporte(String texto, long hora) {
        prefs.edit().putString(ULTIMO_REPORTE, texto).putLong(HORA_REPORTE, hora).apply();
    }

    public Reporte ultimoReporte() {
        return Reporte.leer(prefs.getString(ULTIMO_REPORTE, null));
    }

    public long horaUltimoReporte() {
        return prefs.getLong(HORA_REPORTE, 0);
    }
}
