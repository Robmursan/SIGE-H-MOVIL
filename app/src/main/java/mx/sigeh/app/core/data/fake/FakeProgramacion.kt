package mx.sigeh.app.core.data.fake

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.delay
import mx.sigeh.app.core.auth.PoliticaAmbito
import mx.sigeh.app.core.auth.SessionStore
import mx.sigeh.app.core.common.HoraHospital
import mx.sigeh.app.core.data.ProgramacionRepository
import mx.sigeh.app.core.data.ResultadoProgramacion
import mx.sigeh.app.core.model.EstadoProgramacion
import mx.sigeh.app.core.model.JornadaSemanal

/**
 * Programación semanal Fake: los 7 días de la semana de [desde] con turnos matutino, vespertino y
 * nocturno, y algunos cambios (cancelada, sustituida). Respeta `PROGRAMACION_CONSULTAR` y, para los
 * nombres, `PERSONAL_CONSULTAR`, igual que el backend.
 */
@Singleton
class FakeProgramacionRepository @Inject constructor(
    private val sessionStore: SessionStore,
) : ProgramacionRepository {

    override suspend fun semana(
        hospitalId: String,
        servicioId: String,
        desde: String,
        hasta: String,
        conNombres: Boolean,
        fechaPadron: String,
    ): ResultadoProgramacion {
        delay(Random.nextLong(400, 801))
        val asignaciones = sessionStore.sesionEnMemoria()?.asignaciones
        val facultades = asignaciones?.let { PoliticaAmbito.facultadesEn(servicioId, it) }
        if (facultades != null && !facultades.consultarProgramacion) return ResultadoProgramacion.SinPermiso
        val nombres = conNombres && (facultades?.consultarPersonal ?: true)
        val lunes = HoraHospital.lunesDe(desde.take(10))
        return ResultadoProgramacion.Exito(
            jornadas = (0 until 7).flatMap { dia -> DatosFicticios.programacionDelDia(servicioId, HoraHospital.sumarDias(lunes, dia)) }
                .map { if (nombres) it else it.copy(nombre = null) },
            nombresDisponibles = nombres,
        )
    }
}

/** Programación de un día para la semana Fake. */
internal fun DatosFicticios.programacionDelDia(servicioId: String, fecha: String): List<JornadaSemanal> {
    val manana = HoraHospital.sumarDias(fecha, 1)
    val numeroDia = HoraHospital.diasEntre(HoraHospital.lunesDe(fecha), fecha)
    fun jornada(indice: Int, persona: Int, turno: String, inicio: String, fin: String, estado: EstadoProgramacion) =
        JornadaSemanal(
            programacionId = "sem-$servicioId-$fecha-$indice",
            empleadoId = "emp-sem-${(persona % PERSONAL_SEMANA.size + 1).toString().padStart(4, '0')}",
            nombre = PERSONAL_SEMANA[persona % PERSONAL_SEMANA.size],
            horarioId = "hor-${turno.lowercase()}",
            turnoCodigo = turno,
            inicioProgramado = inicio,
            finProgramado = fin,
            estado = estado,
        )
    val base = numeroDia * 2
    return listOf(
        jornada(1, base, "TM", instanteHospital(fecha, "07:00:00"), instanteHospital(fecha, "14:30:00"), EstadoProgramacion.PROGRAMADA),
        jornada(2, base + 1, "TM", instanteHospital(fecha, "07:00:00"), instanteHospital(fecha, "14:30:00"), EstadoProgramacion.PROGRAMADA),
        jornada(
            3, base + 2, "TM", instanteHospital(fecha, "07:00:00"), instanteHospital(fecha, "14:30:00"),
            if (numeroDia == 2) EstadoProgramacion.CANCELADA else EstadoProgramacion.PROGRAMADA,
        ),
        jornada(
            4, base + 3, "TV", instanteHospital(fecha, "14:30:00"), instanteHospital(fecha, "21:00:00"),
            if (numeroDia == 3) EstadoProgramacion.SUSTITUIDA else EstadoProgramacion.PROGRAMADA,
        ),
        jornada(5, base + 4, "TV", instanteHospital(fecha, "14:30:00"), instanteHospital(fecha, "21:00:00"), EstadoProgramacion.PROGRAMADA),
        jornada(6, base + 5, "TN", instanteHospital(fecha, "21:00:00"), instanteHospital(manana, "07:00:00"), EstadoProgramacion.PROGRAMADA),
    )
}

private val PERSONAL_SEMANA = listOf(
    "Ana Rosales", "Jorge Salas", "Lucía Navarro", "Ricardo Ibarra", "Paola Gutiérrez",
    "Daniela Cruz", "Héctor Ruiz", "Marta Peña", "Sofía Lara", "Tomás Vega",
)
