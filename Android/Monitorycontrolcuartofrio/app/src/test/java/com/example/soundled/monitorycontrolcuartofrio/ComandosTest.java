package com.example.soundled.monitorycontrolcuartofrio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ComandosTest {

    @Test
    public void armaElComandoQueEntiendeElFirmware() {
        assertEquals("@*2*6*80*95*1", Comandos.configuracion(2, 6, 80, 95, true));
        assertEquals("@*-20*-15*50*90*0", Comandos.configuracion(-20, -15, 50, 90, false));
    }

    @Test
    public void validaLosMismosLimitesQueElFirmware() {
        assertEquals(0, Comandos.validar(2, 6, 80, 95));
        assertEquals(0, Comandos.validar(-30, 50, 0, 100));
        assertEquals(R.string.error_rango_temperatura, Comandos.validar(-31, 6, 80, 95));
        assertEquals(R.string.error_rango_temperatura, Comandos.validar(2, 51, 80, 95));
        assertEquals(R.string.error_temp_min_mayor, Comandos.validar(6, 2, 80, 95));
        assertEquals(R.string.error_temp_min_mayor, Comandos.validar(4, 4, 80, 95));
        assertEquals(R.string.error_rango_humedad, Comandos.validar(2, 6, -1, 95));
        assertEquals(R.string.error_rango_humedad, Comandos.validar(2, 6, 80, 101));
        assertEquals(R.string.error_hum_min_mayor, Comandos.validar(2, 6, 95, 80));
    }

    @Test
    public void comparaNumerosConYSinLada() {
        assertTrue(Comandos.mismoNumero("+526671234567", "6671234567"));
        assertTrue(Comandos.mismoNumero("+52 667 123 4567", "526671234567"));
        assertFalse(Comandos.mismoNumero("+526671234567", "6671234568"));
        assertFalse(Comandos.mismoNumero("+526671234567", ""));
        assertFalse(Comandos.mismoNumero(null, "6671234567"));
        assertFalse(Comandos.mismoNumero("AT&T", "AT&T"));
    }
}
