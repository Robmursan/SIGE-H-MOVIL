package mx.sigeh.app.core.data.remote

import java.io.IOException
import javax.inject.Inject
import kotlinx.serialization.SerializationException
import mx.sigeh.app.core.auth.SessionStore
import mx.sigeh.app.core.data.ResultadoRevocacion
import mx.sigeh.app.core.data.ResultadoSesiones
import mx.sigeh.app.core.data.SesionIam
import mx.sigeh.app.core.data.SesionesRepository
import mx.sigeh.app.core.network.iam.IamApi
import mx.sigeh.app.core.network.iam.SessionDto
import retrofit2.HttpException

/** Sesiones reales de IAM V2 (`iamSessions` / `iamRevokeSession`). */
class RemotoSesionesRepository @Inject constructor(
    private val api: IamApi,
    private val sessionStore: SessionStore,
) : SesionesRepository {

    override suspend fun sesiones(): ResultadoSesiones = try {
        ResultadoSesiones.Exito(api.sesiones().map { it.aSesion() })
    } catch (e: HttpException) {
        if (e.code() == 401) ResultadoSesiones.SesionTerminada else ResultadoSesiones.Fallida
    } catch (_: IOException) {
        ResultadoSesiones.Fallida
    } catch (_: SerializationException) {
        ResultadoSesiones.Fallida
    }

    override suspend fun revocar(sessionId: String): ResultadoRevocacion = try {
        val respuesta = api.revocarSesion(sessionId)
        when {
            respuesta.isSuccessful ->
                ResultadoRevocacion.Revocada(eraLaActual = sessionStore.sesionEnMemoria()?.sessionId == sessionId)
            respuesta.code() == 404 -> ResultadoRevocacion.NoEncontrada
            respuesta.code() == 401 -> ResultadoRevocacion.SesionTerminada
            else -> ResultadoRevocacion.Fallida
        }
    } catch (_: IOException) {
        ResultadoRevocacion.Fallida
    }
}

internal fun SessionDto.aSesion() = SesionIam(
    sessionId = sessionId,
    iniciadaEn = createdAt,
    expiraEn = expiresAt,
    inactividadExpiraEn = idleExpiresAt,
    revocadaEn = revokedAt,
    actual = current,
)
