package mx.sigeh.app.core.data.remote

import java.io.IOException
import javax.inject.Inject
import kotlinx.serialization.SerializationException
import mx.sigeh.app.core.auth.AsignacionSesion
import mx.sigeh.app.core.auth.Session
import mx.sigeh.app.core.common.Reloj
import mx.sigeh.app.core.data.AuthRepository
import mx.sigeh.app.core.data.ResultadoLogin
import mx.sigeh.app.core.data.ServidorRepository
import mx.sigeh.app.core.model.RolAlcance
import mx.sigeh.app.core.network.iam.AssignmentDto
import mx.sigeh.app.core.network.iam.IamApi
import mx.sigeh.app.core.network.iam.LoginRequest
import mx.sigeh.app.core.network.iam.OperacionesApi
import retrofit2.HttpException

/**
 * Inicio de sesión real contra IAM V2 de SIGE-H-WEB:
 * `POST /auth/login` y, con el access token recibido, `GET /auth/me` para las asignaciones.
 * Los tokens solo se guardan cifrados (EncryptedSessionStore) y nunca se registran.
 */
class IamAuthRepository @Inject constructor(
    private val api: IamApi,
    private val reloj: Reloj,
) : AuthRepository {

    override suspend fun iniciarSesion(usuario: String, contrasena: String): ResultadoLogin = try {
        val tokens = api.login(LoginRequest(identificador = usuario, password = contrasena))
        val perfil = api.perfil("Bearer ${tokens.accessToken}")
        ResultadoLogin.Exito(
            Session(
                usuarioId = perfil.usuarioId,
                // /auth/me no devuelve nombre: se muestra el identificador hasta que exista ese dato.
                nombreCorto = usuario,
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken,
                sessionId = tokens.sessionId,
                accessExpiraEnMillis = reloj.ahoraMillis() + tokens.expiresIn * 1_000,
                asignaciones = perfil.assignments.map { it.aSesion() },
            ),
        )
    } catch (e: HttpException) {
        when (e.code()) {
            400, 401 -> ResultadoLogin.CredencialesInvalidas
            429 -> ResultadoLogin.LimiteIntentos
            else -> ResultadoLogin.ServicioNoDisponible
        }
    } catch (_: IOException) {
        ResultadoLogin.SinConexion
    } catch (_: SerializationException) {
        ResultadoLogin.ServicioNoDisponible
    }

    override suspend fun cerrarSesion() {
        // Best effort: sin red o con la sesión ya revocada, la purga local continúa igual.
        try {
            api.logout()
        } catch (_: IOException) {
        } catch (_: HttpException) {
        }
    }
}

internal fun AssignmentDto.aSesion() = AsignacionSesion(
    rol = when (kind) {
        "TITULAR" -> RolAlcance.TITULAR
        "ENCARGADO" -> RolAlcance.ENCARGADO
        else -> null
    },
    hospitalId = scope.hospitalId,
    divisionId = scope.divisionId,
    servicioId = scope.servicioId,
    permisos = permissions,
)

/** `GET /meta/version`: no requiere sesión ni base de datos. */
class RemoteServidorRepository @Inject constructor(
    private val api: OperacionesApi,
) : ServidorRepository {
    override val modoReal: Boolean = true

    override suspend fun versionContrato(): String? = try {
        api.version().apiVersion
    } catch (_: IOException) {
        null
    } catch (_: HttpException) {
        null
    } catch (_: SerializationException) {
        null
    }
}
