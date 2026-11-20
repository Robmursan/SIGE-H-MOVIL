package mx.sigeh.app.core.common

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

/** Fuente de tiempo inyectable para que las pruebas sean deterministas. */
interface Reloj {
    fun ahoraMillis(): Long
}

class RelojSistema @Inject constructor() : Reloj {
    override fun ahoraMillis(): Long = System.currentTimeMillis()
}

/** Generador de identificadores. El `clientEventId` se crea UNA sola vez por comando. */
interface GeneradorIds {
    fun nuevoId(): String
}

class GeneradorUuid @Inject constructor() : GeneradorIds {
    override fun nuevoId(): String = UUID.randomUUID().toString()
}

/** Formatos sin java.time (minSdk 24 sin desugaring). */
object FormatoHora {
    fun hora(millis: Long): String = SimpleDateFormat("HH:mm", Locale.ROOT).format(Date(millis))

    fun horaDe(hora: Int, minuto: Int): String = "%02d:%02d".format(Locale.ROOT, hora, minuto)

    /** "07:55" → (7, 55). Devuelve null si el texto no es una hora válida. */
    fun separar(hora: String): Pair<Int, Int>? {
        val partes = hora.split(":")
        if (partes.size != 2) return null
        val h = partes[0].toIntOrNull() ?: return null
        val m = partes[1].toIntOrNull() ?: return null
        return if (h in 0..23 && m in 0..59) h to m else null
    }
}

/** Instante local (zona del teléfono). `mes` va de 1 a 12. */
fun fechaHoraLocal(anio: Int, mes: Int, dia: Int, hora: Int, minuto: Int): Long =
    Calendar.getInstance().apply {
        clear()
        set(anio, mes - 1, dia, hora, minuto, 0)
    }.timeInMillis

/** Último milisegundo del día local de [millis]. */
fun finDelDia(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 23)
    set(Calendar.MINUTE, 59)
    set(Calendar.SECOND, 59)
    set(Calendar.MILLISECOND, 999)
}.timeInMillis
