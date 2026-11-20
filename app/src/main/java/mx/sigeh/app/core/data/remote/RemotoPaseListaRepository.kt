package mx.sigeh.app.core.data.remote

import android.util.Log
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerializationException
import mx.sigeh.app.BuildConfig
import mx.sigeh.app.core.data.CachePaseLista
import mx.sigeh.app.core.data.PaseListaRepository
import mx.sigeh.app.core.data.ResultadoCaptura
import mx.sigeh.app.core.data.ResultadoConsulta
import mx.sigeh.app.core.data.aResultadoCaptura
import mx.sigeh.app.core.data.aResultadoConsulta
import mx.sigeh.app.core.model.AcuseCaptura
import mx.sigeh.app.core.model.EstadoCaptura
import mx.sigeh.app.core.model.JornadaPase
import mx.sigeh.app.core.network.ApiError
import mx.sigeh.app.core.network.ApiErrorParser
import mx.sigeh.app.core.network.asistencia.AsistenciaApi
import mx.sigeh.app.core.network.asistencia.CapturarAsistenciaDto
import retrofit2.HttpException

/**
 * Pase de lista real contra SIGE-H-WEB (`sigeh.api.asistencia=real`). El token lo agrega el
 * `AuthInterceptor` y un 401 se renueva una vez en `SesionAuthenticator`, que reenvía la misma
 * petición: el reintento lleva la **misma** `Idempotency-Key` y el mismo cuerpo.
 *
 * Privacidad: no registra respuestas de `/pases-lista` ni la `Idempotency-Key`; en debug solo deja
 * constancia de los 400 (errores de programación), sin nombres.
 */
@Singleton
class RemotoPaseListaRepository @Inject constructor(
    private val api: AsistenciaApi,
    private val parser: ApiErrorParser,
) : PaseListaRepository {

    private val cache = CachePaseLista()

    override suspend fun consultar(
        hospitalId: String,
        servicioId: String,
        fecha: String,
        horarioId: String?,
        cursor: String?,
    ): ResultadoConsulta = try {
        val pagina = api.pasesLista(
            hospitalId = hospitalId,
            servicioId = servicioId,
            fecha = fecha,
            // El backend responde 400 ante parámetros vacíos: un opcional en blanco no se envía.
            horarioId = horarioId?.takeIf { it.isNotBlank() },
            cursor = cursor?.takeIf { it.isNotBlank() },
            limit = AsistenciaApi.LIMITE_PAGINA,
        ).aPagina()
        cache.registrarPagina(pagina, primeraPagina = cursor.isNullOrBlank())
        ResultadoConsulta.Exito(pagina)
    } catch (e: HttpException) {
        parser.parse(e).also { registrarSiEsErrorDeLaApp("consulta", it) }.aResultadoConsulta()
    } catch (_: IOException) {
        ResultadoConsulta.Fallida
    } catch (_: SerializationException) {
        ResultadoConsulta.Fallida
    }

    override suspend fun capturar(
        programacionId: String,
        estado: EstadoCaptura,
        idempotencyKey: String,
    ): ResultadoCaptura = try {
        val acuse = api.capturar(programacionId, idempotencyKey, CapturarAsistenciaDto.primeraCaptura(estado)).aAcuse()
        cache.registrarAcuse(acuse)
        ResultadoCaptura.Confirmada(acuse)
    } catch (e: HttpException) {
        parser.parse(e).also { registrarSiEsErrorDeLaApp("captura", it) }.aResultadoCaptura()
    } catch (_: IOException) {
        // Timeout o red caída: puede que el servidor sí lo haya guardado. Se reintenta con la misma clave.
        ResultadoCaptura.Incierta
    } catch (_: SerializationException) {
        // 200 con un cuerpo ilegible: el registro existe; un reintento con la misma clave devuelve el acuse.
        ResultadoCaptura.Incierta
    }

    override fun jornada(programacionId: String): JornadaPase? = cache.jornada(programacionId)

    override fun acuse(programacionId: String): AcuseCaptura? = cache.acuse(programacionId)

    override fun zonaHoraria(): String? = cache.zonaHoraria()

    override fun limpiar() = cache.limpiar()

    private fun registrarSiEsErrorDeLaApp(operacion: String, error: ApiError) {
        if (BuildConfig.DEBUG && error.httpCode == 400) {
            Log.w(TAG, "400 en $operacion de asistencia: ${error.causa ?: error.message}")
        }
    }

    private companion object {
        const val TAG = "SigehAsistencia"
    }
}
