package com.jonagmz.cuartofrio.protocolo

/** Textos de los comandos SMS hacia el equipo. */
object Comandos {
    private const val MAX_SMS = 160

    private fun base(pin: String) = if (pin.isBlank()) "CF2" else "CF2 ${pin.trim()}"

    fun info(pin: String) = "${base(pin)} INFO"
    fun configuracion(pin: String) = "${base(pin)} CFG"
    fun encender(pin: String, encendido: Boolean) = "${base(pin)} ${if (encendido) "ON" else "OFF"}"
    fun reconocer(pin: String) = "${base(pin)} ACK"
    fun historial(pin: String, horas: Int = 48) = "${base(pin)} HIST ${horas.coerceIn(1, 255)}"

    /**
     * SET solo con los valores que cambiaron. Si no caben en un SMS se reparten en varios; el firmware valida
     * cada SET completo, así que se agrupan de forma que cada uno sea coherente por sí mismo cuando es posible.
     */
    fun cambios(pin: String, anterior: AjustesEquipo?, nuevo: AjustesEquipo): List<String> {
        val antes = anterior?.comoCampos().orEmpty()
        val cambiados = nuevo.comoCampos().filter { (k, v) -> antes[k] != v }
        if (cambiados.isEmpty()) return emptyList()
        val comandos = mutableListOf<String>()
        var actual = StringBuilder("${base(pin)} SET")
        for ((k, v) in cambiados) {
            val parte = " $k=$v"
            if (actual.length + parte.length > MAX_SMS) {
                comandos += actual.toString()
                actual = StringBuilder("${base(pin)} SET")
            }
            actual.append(parte)
        }
        comandos += actual.toString()
        return comandos
    }

    /** Compara los últimos 10 dígitos: "+526671234567" y "667 123 4567" son el mismo número. */
    fun mismoNumero(a: String?, b: String?): Boolean {
        val da = a.orEmpty().filter(Char::isDigit)
        val db = b.orEmpty().filter(Char::isDigit)
        return da.length >= 10 && db.length >= 10 && da.takeLast(10) == db.takeLast(10)
    }
}
