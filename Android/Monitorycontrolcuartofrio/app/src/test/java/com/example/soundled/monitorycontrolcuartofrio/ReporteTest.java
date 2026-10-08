package com.example.soundled.monitorycontrolcuartofrio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ReporteTest {

    @Test
    public void leeElFormatoDelFirmwareOriginal() {
        Reporte r = Reporte.leer("4,85,Cuarto frio no. 1,0,1,0,0");
        assertEquals("4", r.temperatura);
        assertEquals("85", r.humedad);
        assertEquals("Cuarto frio no. 1", r.ubicacion);
        assertTrue(r.alarmaTempAlta);
        assertFalse(r.alarmaTempBaja);
        assertFalse(r.tieneEstado);
        assertTrue(r.hayAlarma());
    }

    @Test
    public void leeElFormatoDelFirmwareNuevo() {
        Reporte r = Reporte.leer("-18.5,60.0,Congelador,0,0,0,0,0,1,1,-20,-15,50,90");
        assertEquals("-18.5", r.temperatura);
        assertTrue(r.tieneEstado);
        assertTrue(r.enfriando);
        assertTrue(r.sistemaEncendido);
        assertEquals(-20, r.tempMin);
        assertEquals(-15, r.tempMax);
        assertEquals(50, r.humMin);
        assertEquals(90, r.humMax);
        assertFalse(r.hayAlarma());
    }

    @Test
    public void errorDeSensorCuentaComoAlarma() {
        Reporte r = Reporte.leer("ND,ND,Cuarto 1,0,0,0,0,1,0,1,2,6,80,95");
        assertTrue(r.errorSensor);
        assertTrue(r.hayAlarma());
    }

    @Test
    public void ignoraSmsQueNoSonReportes() {
        assertNull(Reporte.leer(null));
        assertNull(Reporte.leer("Hola, ¿cómo estás?"));
        assertNull(Reporte.leer("Tu saldo es de $50, vence el 10,11,12,13"));
        assertNull(Reporte.leer("4,85,Cuarto,0,0,0"));
        assertNull(Reporte.leer("4,85,Cuarto,0,2,0,0"));
        assertNull(Reporte.leer("4,85,Cuarto,0,0,0,0,0,1,1,dos,6,80,95"));
        assertNull(Reporte.leer("ERROR: configuracion invalida. Formato @*tMin*tMax*hMin*hMax*0|1"));
    }
}
