package mx.sigeh.app.core.data.fake

import javax.inject.Inject
import kotlinx.coroutines.delay
import mx.sigeh.app.core.auth.ContextoPropio
import mx.sigeh.app.core.auth.SessionStore
import mx.sigeh.app.core.data.ContextosRepository
import mx.sigeh.app.core.data.EtiquetasPorIdentificador
import mx.sigeh.app.core.data.ResultadoContextos
import mx.sigeh.app.core.model.RolAlcance

/**
 * `GET /auth/me/contextos` en modo demostración. Igual que el backend, solo devuelve contextos de la
 * sesión vigente: se construyen con las asignaciones de la sesión Fake (las mismas de `/auth/me`) y
 * los nombres de [DatosFicticios]. Una sesión guardada por M0 (sin asignaciones) no recibe ninguno.
 *
 * - Un identificador sin nombre ficticio usa el mismo texto que el modo real («Servicio ··· c91e»).
 * - Los ENCARGADO llevan [DatosFicticios.CONTEXTO_ENCARGO_HASTA]; los TITULAR no tienen término.
 */
class FakeContextosRepository @Inject constructor(
    private val sessionStore: SessionStore,
) : ContextosRepository {

    override suspend fun contextosPropios(): ResultadoContextos {
        delay(LATENCIA_MS)
        val sesion = sessionStore.actual()?.takeUnless { it.vencida } ?: return ResultadoContextos.SesionVencida
        val contextos = sesion.asignaciones.orEmpty().mapIndexed { indice, asignacion ->
            ContextoPropio(
                assignmentId = "asig-fake-${indice + 1}",
                hospitalId = asignacion.hospitalId,
                hospitalNombre = nombre(asignacion.hospitalId, "Hospital"),
                zonaHoraria = DatosFicticios.ZONA_HOSPITAL,
                divisionId = asignacion.divisionId,
                divisionNombre = asignacion.divisionId?.let { nombre(it, "División") },
                servicioId = asignacion.servicioId,
                servicioNombre = asignacion.servicioId?.let { nombre(it, "Servicio") },
                rol = asignacion.rol,
                vigenteDesde = DatosFicticios.CONTEXTO_VIGENTE_DESDE,
                vigenteHasta = DatosFicticios.CONTEXTO_ENCARGO_HASTA.takeIf { asignacion.rol == RolAlcance.ENCARGADO },
                permisos = asignacion.permisos,
            )
        }
        return ResultadoContextos.Exito(contextos)
    }

    private fun nombre(id: String, tipo: String): String =
        DatosFicticios.nombresContexto[id] ?: EtiquetasPorIdentificador.etiqueta(tipo, id)

    private companion object {
        const val LATENCIA_MS = 300L
    }
}
