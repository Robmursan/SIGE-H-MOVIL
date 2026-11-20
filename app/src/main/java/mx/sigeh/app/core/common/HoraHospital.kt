package mx.sigeh.app.core.common

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Fechas y horas en la **zona del hospital**, nunca en la del teléfono (prompt M2, regla 12).
 * Sin java.time (minSdk 24 sin desugaring): `Calendar`/`TimeZone` y un parser ISO-8601 propio que
 * acepta `Z` u offset, con o sin segundos y con fracciones de cualquier largo.
 *
 * Las fechas locales (`fecha` del query, días de la semana) viajan como `YYYY-MM-DD`.
 */
object HoraHospital {

    /** Zona de la primera consulta; después se adopta la `zonaHoraria` que envía el servidor. */
    const val ZONA_POR_DEFECTO = "America/Mexico_City"

    private const val HORA_MS = 3_600_000L
    private const val DIA_MS = 24 * HORA_MS

    /** Máximo de `/programaciones` (backend: «Intervalo inválido: máximo 168 horas»). */
    const val VENTANA_MAXIMA_MS = 168 * HORA_MS

    private val ISO = Regex(
        """^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2})(?:[.,](\d{1,9}))?)?(Z|z|[+-]\d{2}(?::?\d{2})?)$""",
    )
    private val FECHA = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")

    private val DIAS = listOf("Dom", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb")
    private val DIAS_LARGOS = listOf("Domingo", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado")
    private val MESES = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

    /** Instante (ms UTC) de un ISO-8601 con zona; null si el texto no es válido. */
    fun instante(iso: String): Long? {
        val m = ISO.matchEntire(iso.trim()) ?: return null
        val g = m.groupValues
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(g[1].toInt(), g[2].toInt() - 1, g[3].toInt(), g[4].toInt(), g[5].toInt(), g[6].ifEmpty { "0" }.toInt())
            set(Calendar.MILLISECOND, g[7].ifEmpty { "0" }.padEnd(3, '0').take(3).toInt())
        }
        return utc.timeInMillis - offsetMs(g[8])
    }

    private fun offsetMs(texto: String): Long {
        if (texto.equals("Z", ignoreCase = true)) return 0
        val signo = if (texto.startsWith("-")) -1 else 1
        val digitos = texto.drop(1).replace(":", "")
        val horas = digitos.take(2).toLong()
        val minutos = digitos.drop(2).ifEmpty { "0" }.toLong()
        return signo * (horas * HORA_MS + minutos * 60_000)
    }

    /** Zona IANA válida o la de por defecto (un identificador desconocido no debe convertirse en GMT). */
    fun zona(id: String?): TimeZone {
        val candidata = id?.trim().orEmpty()
        val tz = TimeZone.getTimeZone(candidata)
        val valida = candidata.isNotEmpty() && (tz.id == candidata || candidata in setOf("UTC", "GMT"))
        return if (valida) tz else TimeZone.getTimeZone(ZONA_POR_DEFECTO)
    }

    private fun formato(patron: String, zona: String) =
        SimpleDateFormat(patron, Locale.ROOT).apply { timeZone = zona(zona) }

    /** `HH:mm` de un instante ISO en la zona; «--:--» si no se puede leer. */
    fun hora(iso: String, zona: String): String = instante(iso)?.let { hora(it, zona) } ?: "--:--"

    fun hora(millis: Long, zona: String): String = formato("HH:mm", zona).format(Date(millis))

    /** `HH:mm:ss` de un instante ISO en la zona (acuse del servidor). */
    fun horaConSegundos(iso: String, zona: String): String =
        instante(iso)?.let { formato("HH:mm:ss", zona).format(Date(it)) } ?: "--:--:--"

    /** «HH:mm–HH:mm». */
    fun intervalo(inicioIso: String, finIso: String, zona: String): String = "${hora(inicioIso, zona)}–${hora(finIso, zona)}"

    /** Fecha local `YYYY-MM-DD` de un instante en la zona. */
    fun fechaDe(millis: Long, zona: String): String = formato("yyyy-MM-dd", zona).format(Date(millis))

    /** «Hoy» en la zona del hospital. */
    fun hoy(ahoraMillis: Long, zona: String): String = fechaDe(ahoraMillis, zona)

    /** Calendario UTC a medianoche de una fecha local (para aritmética de días sin depender de zonas). */
    private fun dia(fecha: String): Calendar? {
        val m = FECHA.matchEntire(fecha) ?: return null
        val (a, mes, d) = m.destructured
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(a.toInt(), mes.toInt() - 1, d.toInt())
        }
    }

    private fun Calendar.comoFecha(): String =
        String.format(Locale.ROOT, "%04d-%02d-%02d", get(Calendar.YEAR), get(Calendar.MONTH) + 1, get(Calendar.DAY_OF_MONTH))

    fun sumarDias(fecha: String, dias: Int): String =
        dia(fecha)?.apply { add(Calendar.DAY_OF_MONTH, dias) }?.comoFecha() ?: fecha

    /** Lunes de la semana de [fecha] (las semanas van de lunes a domingo). */
    fun lunesDe(fecha: String): String {
        val cal = dia(fecha) ?: return fecha
        val desdeLunes = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
        return sumarDias(fecha, -desdeLunes)
    }

    /** «Vie 20 nov». */
    fun fechaCorta(fecha: String): String {
        val cal = dia(fecha) ?: return fecha
        return "${DIAS[cal.get(Calendar.DAY_OF_WEEK) - 1]} ${cal.get(Calendar.DAY_OF_MONTH)} ${MESES[cal.get(Calendar.MONTH)]}"
    }

    /** «Viernes 20 nov 2026». */
    fun fechaLarga(fecha: String): String {
        val cal = dia(fecha) ?: return fecha
        return "${DIAS_LARGOS[cal.get(Calendar.DAY_OF_WEEK) - 1]} ${cal.get(Calendar.DAY_OF_MONTH)} " +
            "${MESES[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.YEAR)}"
    }

    /** «Lun 16». */
    fun diaCorto(fecha: String): String {
        val cal = dia(fecha) ?: return fecha
        return "${DIAS[cal.get(Calendar.DAY_OF_WEEK) - 1]} ${cal.get(Calendar.DAY_OF_MONTH)}"
    }

    /** «16–22 nov 2026» o «30 nov – 6 dic 2026». */
    fun rangoSemana(lunes: String): String {
        val inicio = dia(lunes) ?: return lunes
        val fin = dia(sumarDias(lunes, 6)) ?: return lunes
        val mismoMes = inicio.get(Calendar.MONTH) == fin.get(Calendar.MONTH)
        val anio = fin.get(Calendar.YEAR)
        val mesInicio = MESES[inicio.get(Calendar.MONTH)]
        val mesFin = MESES[fin.get(Calendar.MONTH)]
        return if (mismoMes) {
            "${inicio.get(Calendar.DAY_OF_MONTH)}–${fin.get(Calendar.DAY_OF_MONTH)} $mesFin $anio"
        } else {
            "${inicio.get(Calendar.DAY_OF_MONTH)} $mesInicio – ${fin.get(Calendar.DAY_OF_MONTH)} $mesFin $anio"
        }
    }

    /** «20 nov 07:04» de un ISO en la zona (fechas de sesión). */
    fun fechaHora(iso: String, zona: String): String {
        val millis = instante(iso) ?: return iso
        val cal = Calendar.getInstance(zona(zona)).apply { timeInMillis = millis }
        return "${cal.get(Calendar.DAY_OF_MONTH)} ${MESES[cal.get(Calendar.MONTH)]} ${hora(millis, zona)}"
    }

    /** Instante de las 00:00 locales de [fecha] en la zona. */
    fun inicioDelDiaMillis(fecha: String, zona: String): Long {
        val utc = dia(fecha) ?: return 0
        return Calendar.getInstance(zona(zona)).apply {
            clear()
            set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
        }.timeInMillis
    }

    /** ISO con el offset de la zona en ese instante: `2026-11-16T00:00:00-06:00`. */
    fun isoConOffset(millis: Long, zona: String): String = formato("yyyy-MM-dd'T'HH:mm:ssXXX", zona).format(Date(millis))

    /**
     * Ventana de una semana para `/programaciones`: de lunes 00:00 a lunes 00:00 en la zona del
     * hospital, recortada a 168 h si un cambio de horario la alargara.
     */
    fun ventanaSemana(lunes: String, zona: String): Pair<String, String> {
        val desde = inicioDelDiaMillis(lunes, zona)
        val hasta = minOf(inicioDelDiaMillis(sumarDias(lunes, 7), zona), desde + VENTANA_MAXIMA_MS)
        return isoConOffset(desde, zona) to isoConOffset(hasta, zona)
    }

    /** Días entre dos fechas locales (b − a). */
    fun diasEntre(a: String, b: String): Int {
        val ca = dia(a) ?: return 0
        val cb = dia(b) ?: return 0
        return ((cb.timeInMillis - ca.timeInMillis) / DIA_MS).toInt()
    }
}
