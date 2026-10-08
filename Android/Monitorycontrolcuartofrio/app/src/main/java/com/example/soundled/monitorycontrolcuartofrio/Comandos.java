package com.example.soundled.monitorycontrolcuartofrio;

/** Comandos SMS que entiende el equipo y validación de los valores, igual que en el firmware. */
public final class Comandos {

    public static final int TEMP_LIMITE_MIN = -30;
    public static final int TEMP_LIMITE_MAX = 50;
    public static final String PEDIR_LECTURAS = "INFO";

    private Comandos() {
    }

    /** Devuelve el id del texto de error, o 0 si los valores son válidos. */
    public static int validar(int tempMin, int tempMax, int humMin, int humMax) {
        if (tempMin < TEMP_LIMITE_MIN || tempMax > TEMP_LIMITE_MAX) {
            return R.string.error_rango_temperatura;
        }
        if (tempMin >= tempMax) {
            return R.string.error_temp_min_mayor;
        }
        if (humMin < 0 || humMax > 100) {
            return R.string.error_rango_humedad;
        }
        if (humMin >= humMax) {
            return R.string.error_hum_min_mayor;
        }
        return 0;
    }

    public static String configuracion(int tempMin, int tempMax, int humMin, int humMax, boolean encendido) {
        return "@*" + tempMin + "*" + tempMax + "*" + humMin + "*" + humMax + "*" + (encendido ? 1 : 0);
    }

    /** Compara los últimos 10 dígitos, así "+526671234567" y "6671234567" son el mismo número. */
    public static boolean mismoNumero(String a, String b) {
        String da = soloDigitos(a), db = soloDigitos(b);
        if (da.length() < 10 || db.length() < 10) {
            return false;
        }
        return da.substring(da.length() - 10).equals(db.substring(db.length() - 10));
    }

    private static String soloDigitos(String s) {
        return s == null ? "" : s.replaceAll("[^0-9]", "");
    }
}
