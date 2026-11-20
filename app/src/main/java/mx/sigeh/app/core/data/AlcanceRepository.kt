package mx.sigeh.app.core.data

import mx.sigeh.app.core.model.Alcance
import mx.sigeh.app.core.model.Turno

/** IAM/ORG/CAL: alcances vigentes (CU-IAM-05) y turnos del servicio. */
interface AlcanceRepository {
    suspend fun alcancesVigentes(): List<Alcance>

    suspend fun turnos(servicioId: String): List<Turno>

    /** Nombre de un servicio para mostrar registros de otro ámbito (por ejemplo, un comando denegado). */
    suspend fun nombreServicio(servicioId: String): String?
}
