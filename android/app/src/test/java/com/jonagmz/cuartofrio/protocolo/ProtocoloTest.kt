package com.jonagmz.cuartofrio.protocolo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Los textos de ejemplo son los que produce el firmware en firmware/pruebas. */
class ProtocoloTest {

    @Test
    fun leeElEstado() {
        val e = Protocolo.leer("CF2;R;T3.5;H84;C30.0;P50;E1;A0;S3.5;Q21;U3330;B0") as MensajeEquipo.Estado
        assertEquals(3.5f, e.temperatura)
        assertEquals(84, e.humedad)
        assertEquals(30f, e.ladoCaliente)
        assertEquals(50, e.potencia)
        assertTrue(e.encendido)
        assertEquals(0, e.alarmas)
        assertEquals(3.5f, e.objetivo)
        assertEquals(21, e.senal)
        assertEquals(3330L, e.minutosEncendido)
        assertFalse(e.bloqueoTermico)
    }

    @Test
    fun leeElBloqueoTermicoYAceptaElFormatoSinB() {
        assertTrue((Protocolo.leer("CF2;R;T9.0;H80;C31.0;P0;E1;A21;S4.0;Q21;U90;B1") as MensajeEquipo.Estado).bloqueoTermico)
        assertFalse((Protocolo.leer("CF2;R;T9.0;H80;C31.0;P0;E1;A21;S4.0;Q21;U90") as MensajeEquipo.Estado).bloqueoTermico)
    }

    @Test
    fun leeEstadoConSensoresEnFallaYAlarmasEnHexadecimal() {
        val e = Protocolo.leer("CF2;R;T--;H--;C--;P25;E1;A51;S-1.0;Q99;U5") as MensajeEquipo.Estado
        assertNull(e.temperatura)
        assertNull(e.humedad)
        assertNull(e.ladoCaliente)
        assertNull(e.senal)
        assertEquals(-1f, e.objetivo)
        assertEquals(Alarma.TEMP_ALTA or Alarma.SENSOR or Alarma.SENSOR_CALIENTE, e.alarmas)
        assertEquals(listOf("Temperatura alta", "Falla del sensor de ambiente", "Falla del sensor del lado caliente"), Alarma.nombres(e.alarmas))
    }

    @Test
    fun leeLaConfiguracionDeFabrica() {
        val c = Protocolo.leer("CF2;C;SP4.0;TL1.0;TH8.0;HL60;HH95;AD10;RI60;RA4;HI15;PM100;PC65;KP20;KI1;KD0;EN0;MR") as MensajeEquipo.Configuracion
        assertEquals(AjustesEquipo(), c.ajustes)
    }

    @Test
    fun leeConfiguracionConDecimalesYNegativos() {
        val c = Protocolo.leer("CF2;C;SP-1.0;TL-2.5;TH7.0;HL60;HH95;AD1;RI0;RA4;HI15;PM100;PC65;KP20;KI0.5;KD0;EN1;MP") as MensajeEquipo.Configuracion
        assertEquals(-1f, c.ajustes.objetivo)
        assertEquals(-2.5f, c.ajustes.alarmaTempBaja)
        assertEquals(0.5f, c.ajustes.ki)
        assertTrue(c.ajustes.encendido)
        assertEquals('P', c.ajustes.modo)
    }

    @Test
    fun decodificaUnaPaginaDeHistorial() {
        // 2 muestras: 3.5 °C, 84 %, 30 °C, potencia 8/15 + alarma de rango; y la marca de reinicio
        val bytes = byteArrayOf(7, 84, 30, (0x10 or 8).toByte(), 0x80.toByte(), 254.toByte(), 255.toByte(), 0)
        val texto = "CF2;H;8/8;I15;E7;N0;" + base64Url(bytes)
        val p = Protocolo.leer(texto) as MensajeEquipo.PaginaHistorial
        assertEquals(8, p.pagina)
        assertEquals(8, p.total)
        assertEquals(15, p.intervaloMin)
        assertEquals(7, p.minutosDesdeUltima)
        assertEquals(0, p.restantes)
        assertEquals(Muestra(3.5f, 84, 30, 53, 1, false), p.muestras[0])
        assertTrue(p.muestras[1].marcaInicio)
        assertNull(p.muestras[1].temperatura)
    }

    @Test
    fun temperaturasNegativasEnElHistorial() {
        val m = Protocolo.decodificarMuestras(base64Url(byteArrayOf((-37).toByte(), 70, 255.toByte(), 15)))
        assertEquals(-18.5f, m[0].temperatura)
        assertNull(m[0].ladoCaliente)
        assertEquals(100, m[0].potencia)
    }

    @Test
    fun leeEventosYErrores() {
        assertEquals(MensajeEquipo.Evento("INICIO", "LUZ"), Protocolo.leer("CF2;E;INICIO;LUZ"))
        assertEquals(MensajeEquipo.Error("PIN"), Protocolo.leer("CF2;X;PIN"))
    }

    @Test
    fun ignoraLoQueNoEsDelEquipo() {
        assertNull(Protocolo.leer("Hola, ¿cómo estás?"))
        assertNull(Protocolo.leer("Tu saldo es de $50"))
        assertNull(Protocolo.leer("CF2;R;T3.5"))          // incompleto
        assertNull(Protocolo.leer("CF2;H;1/2;I15;E0;N0;*"))  // base64 inválido
        assertNull(Protocolo.leer("CF2;Z;algo"))
    }

    @Test
    fun comandos() {
        assertEquals("CF2 4321 INFO", Comandos.info("4321"))
        assertEquals("CF2 INFO", Comandos.info(""))
        assertEquals("CF2 4321 HIST 48", Comandos.historial("4321"))
        assertEquals("CF2 4321 OFF", Comandos.encender("4321", false))
    }

    @Test
    fun setSoloConLoQueCambio() {
        val antes = AjustesEquipo()
        val despues = antes.copy(objetivo = 3.5f, alarmaTempAlta = 7f, ki = 0.5f, encendido = true)
        assertEquals(listOf("CF2 4321 SET SP=3.5 TH=7.0 KI=0.5 EN=1"), Comandos.cambios("4321", antes, despues))
        assertEquals(emptyList<String>(), Comandos.cambios("4321", antes, antes))
        val todo = Comandos.cambios("4321", null, antes).single()
        assertTrue(todo.length <= 160)
        assertTrue(todo.startsWith("CF2 4321 SET SP=4.0 TL=1.0 TH=8.0"))
    }

    @Test
    fun validaComoElFirmware() {
        assertTrue(AjustesEquipo().validar().isEmpty())
        assertFalse(AjustesEquipo(objetivo = 3.3f).validar().isEmpty())
        assertFalse(AjustesEquipo(objetivo = 9f, alarmaTempAlta = 7f).validar().isEmpty())
        assertFalse(AjustesEquipo(alarmaHumBaja = 90, alarmaHumAlta = 80).validar().isEmpty())
        assertFalse(AjustesEquipo(reporteMin = 3).validar().isEmpty())
        assertTrue(AjustesEquipo(reporteMin = 0).validar().isEmpty())
        assertTrue(AjustesEquipo(objetivo = -18f, alarmaTempBaja = -22f, alarmaTempAlta = -15f).validar().isEmpty())
    }

    @Test
    fun comparaNumeros() {
        assertTrue(Comandos.mismoNumero("+526671234567", "667 123 4567"))
        assertFalse(Comandos.mismoNumero("+526671234567", "6671234568"))
        assertFalse(Comandos.mismoNumero(null, "6671234567"))
    }

    private fun base64Url(bytes: ByteArray) = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
