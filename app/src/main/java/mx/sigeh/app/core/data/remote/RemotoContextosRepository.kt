package mx.sigeh.app.core.data.remote

import java.io.IOException
import javax.inject.Inject
import kotlinx.serialization.SerializationException
import mx.sigeh.app.core.auth.ContextoPropio
import mx.sigeh.app.core.data.ContextosRepository
import mx.sigeh.app.core.data.ResultadoContextos
import mx.sigeh.app.core.model.RolAlcance
import mx.sigeh.app.core.network.iam.IamApi
import mx.sigeh.app.core.network.iam.OwnContextDto
import retrofit2.HttpException

/**
 * `GET /auth/me/contextos` contra IAM V2 de SIGE-H-WEB. El bearer lo agrega `AuthInterceptor` y un
 * 401 se renueva una sola vez en `SesionAuthenticator`; si aun así llega 401, la sesión terminó.
 * No registra la respuesta: los nombres de hospital y servicio son datos institucionales.
 */
class RemotoContextosRepository @Inject constructor(
    private val api: IamApi,
) : ContextosRepository {

    override suspend fun contextosPropios(): ResultadoContextos = try {
        ResultadoContextos.Exito(api.contextos().map { it.aContexto() })
    } catch (e: HttpException) {
        if (e.code() == 401) ResultadoContextos.SesionVencida else ResultadoContextos.NoDisponible(e.code())
    } catch (_: IOException) {
        ResultadoContextos.SinConexion
    } catch (_: SerializationException) {
        ResultadoContextos.NoDisponible(null)
    }
}

internal fun OwnContextDto.aContexto() = ContextoPropio(
    assignmentId = assignmentId,
    hospitalId = hospitalId,
    hospitalNombre = hospitalNombre,
    zonaHoraria = zonaHoraria,
    divisionId = divisionId,
    divisionNombre = divisionNombre,
    servicioId = servicioId,
    servicioNombre = servicioNombre,
    rol = when (tipo) {
        "TITULAR" -> RolAlcance.TITULAR
        "ENCARGADO" -> RolAlcance.ENCARGADO
        else -> null
    },
    vigenteDesde = vigenteDesde,
    vigenteHasta = vigenteHasta,
    permisos = permisos,
)
