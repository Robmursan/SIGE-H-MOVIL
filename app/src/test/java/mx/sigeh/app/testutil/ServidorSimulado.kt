package mx.sigeh.app.testutil

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mx.sigeh.app.core.model.SigehJson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okio.Buffer
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Respuestas con la forma exacta del backend SIGE-H-WEB (main 2604aa6):
 * DTO de `auth.dto.ts` y errores por defecto de NestJS `{statusCode, message, error}`.
 * Todos los datos son ficticios.
 */
object ContratoIam {
    const val USUARIO_ID = "6a1f4e2c-0000-4000-8000-000000000001"
    const val SESION_ID = "6a1f4e2c-0000-4000-8000-0000000000aa"
    const val HOSPITAL_ID = "6a1f4e2c-0000-4000-8000-0000000000b1"
    const val DIVISION_ID = "6a1f4e2c-0000-4000-8000-0000000000c1"
    const val SERVICIO_ID = "6a1f4e2c-0000-4000-8000-0000000000d1"
    val REFRESH_1 = "r1".padEnd(64, 'x')
    val REFRESH_2 = "r2".padEnd(64, 'y')

    fun tokens(acceso: String, refresh: String) =
        """{"accessToken":"$acceso","refreshToken":"$refresh","tokenType":"Bearer","expiresIn":300,"sessionId":"$SESION_ID"}"""

    val PERFIL = """
        {"usuarioId":"$USUARIO_ID","sessionId":"$SESION_ID","provider":"local","assignments":[
          {"kind":"TITULAR","scope":{"hospitalId":"$HOSPITAL_ID","divisionId":"$DIVISION_ID","servicioId":"$SERVICIO_ID"},"permissions":["PERMISO_FICTICIO"]},
          {"kind":null,"scope":{"hospitalId":"$HOSPITAL_ID","divisionId":null,"servicioId":null},"permissions":[]}
        ]}
    """.trimIndent()

    fun errorNest(codigo: Int, mensaje: String, error: String) =
        """{"statusCode":$codigo,"message":"$mensaje","error":"$error"}"""
}

fun respuestaJson(codigo: Int, cuerpo: String): MockResponse = MockResponse.Builder()
    .code(codigo)
    .addHeader("Content-Type", "application/json")
    .body(Buffer().writeUtf8(cuerpo))
    .build()

fun retrofitPara(servidor: MockWebServer, cliente: OkHttpClient = OkHttpClient()): Retrofit = Retrofit.Builder()
    .baseUrl(servidor.url("/api/v1/"))
    .client(cliente)
    .addConverterFactory(SigehJson.asConverterFactory("application/json".toMediaType()))
    .build()
