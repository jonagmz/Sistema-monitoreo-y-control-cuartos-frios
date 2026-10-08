package com.example.soundled.monitorycontrolcuartofrio;

/**
 * Reporte que envía el equipo por SMS:
 * temp,hum,ubicacion,alarmaTempBaja,alarmaTempAlta,alarmaHumBaja,alarmaHumAlta[,errorSensor,enfriando,
 * sistema,tempMin,tempMax,humMin,humMax]
 * Los 7 primeros campos son los del firmware original; el resto solo lo manda el firmware nuevo.
 */
public final class Reporte {

    public final String temperatura;
    public final String humedad;
    public final String ubicacion;
    public final boolean alarmaTempBaja, alarmaTempAlta, alarmaHumBaja, alarmaHumAlta;
    /** Solo disponible con el firmware nuevo. */
    public final boolean tieneEstado;
    public final boolean errorSensor, enfriando, sistemaEncendido;
    public final int tempMin, tempMax, humMin, humMax;

    private Reporte(String[] c) {
        temperatura = c[0].trim();
        humedad = c[1].trim();
        ubicacion = c[2].trim();
        alarmaTempBaja = bit(c[3]);
        alarmaTempAlta = bit(c[4]);
        alarmaHumBaja = bit(c[5]);
        alarmaHumAlta = bit(c[6]);
        tieneEstado = c.length >= 14;
        errorSensor = tieneEstado && bit(c[7]);
        enfriando = tieneEstado && bit(c[8]);
        sistemaEncendido = tieneEstado && bit(c[9]);
        tempMin = tieneEstado ? Integer.parseInt(c[10].trim()) : 0;
        tempMax = tieneEstado ? Integer.parseInt(c[11].trim()) : 0;
        humMin = tieneEstado ? Integer.parseInt(c[12].trim()) : 0;
        humMax = tieneEstado ? Integer.parseInt(c[13].trim()) : 0;
    }

    /** Devuelve null si el texto no es un reporte del equipo (por ejemplo, un SMS cualquiera). */
    public static Reporte leer(String texto) {
        if (texto == null) {
            return null;
        }
        String[] campos = texto.trim().split(",", -1);
        if (campos.length != 7 && campos.length != 14) {
            return null;
        }
        for (int i = 3; i < campos.length && i < 10; i++) {
            if (!campos[i].trim().equals("0") && !campos[i].trim().equals("1")) {
                return null;
            }
        }
        try {
            return new Reporte(campos);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public boolean hayAlarma() {
        return alarmaTempBaja || alarmaTempAlta || alarmaHumBaja || alarmaHumAlta || errorSensor;
    }

    private static boolean bit(String campo) {
        return campo.trim().equals("1");
    }
}
