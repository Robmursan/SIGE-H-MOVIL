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
 * Respuestas con la forma exacta del backend SIGE-H-WEB (main 2604aa6; sesiones desde 2d1ec62):
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

    /** `GET /auth/sessions`: la actual, una revocada y otra activa. */
    val SESIONES = """
        [
          {"sessionId":"$SESION_ID","createdAt":"2026-11-20T13:00:00.000Z","expiresAt":"2026-11-20T21:00:00.000Z","idleExpiresAt":"2026-11-20T13:30:00.000Z","revokedAt":null,"current":true},
          {"sessionId":"6a1f4e2c-0000-4000-8000-0000000000ab","createdAt":"2026-11-19T13:00:00.000Z","expiresAt":"2026-11-19T21:00:00.000Z","idleExpiresAt":"2026-11-19T13:30:00.000Z","revokedAt":"2026-11-19T14:00:00.000Z","current":false},
          {"sessionId":"6a1f4e2c-0000-4000-8000-0000000000ac","createdAt":"2026-11-20T12:00:00.000Z","expiresAt":"2026-11-20T20:00:00.000Z","idleExpiresAt":"2026-11-20T12:30:00.000Z","current":false}
        ]
    """.trimIndent()
}

/**
 * Respuestas de asistencia operativa con la forma exacta del contrato `sigeh-v1` (SIGE-H-WEB main
 * 2d1ec62, `asistencia.dto.ts`). Datos ficticios. El hospital y el servicio son los de [ContratoIam].
 */
object ContratoAsistencia {
    const val FECHA = "2026-11-20"
    const val ZONA = "America/Mexico_City"
    const val PROGRAMACION_CAPTURADA = "7b2e0000-0000-4000-8000-000000000001"
    const val PROGRAMACION_PENDIENTE = "7b2e0000-0000-4000-8000-000000000002"
    const val PROGRAMACION_CANCELADA = "7b2e0000-0000-4000-8000-000000000003"
    const val HORARIO_TM = "7b2e0000-0000-4000-8000-0000000000f1"
    const val CURSOR = "7b2e0000-0000-4000-8000-000000000003"
    const val ASISTENCIA_ID = "7b2e0000-0000-4000-8000-0000000000e1"

    /** Mayor que 2^53: se perdería si se leyera como número de punto flotante (JavaScript) o se redondeara. */
    const val EVENTO_GRANDE = "9007199254740993"

    /**
     * Tres filas: una ya capturada (con `ultimaCaptura`), una por capturar (nulos explícitos) y una
     * cancelada sin horario ni turno (nulos omitidos). Incluye un campo desconocido que se debe ignorar.
     */
    val PAGINA = """
        {"hospitalId":"${ContratoIam.HOSPITAL_ID}","servicioId":"${ContratoIam.SERVICIO_ID}","fecha":"$FECHA","zonaHoraria":"$ZONA",
         "items":[
          {"programacionId":"$PROGRAMACION_CAPTURADA",
           "empleado":{"empleadoId":"7b2e0000-0000-4000-8000-0000000000a1","nombres":"Ana Sofía","primerApellido":"Ficticia"},
           "horarioId":"$HORARIO_TM","turnoCodigo":"TM",
           "inicioProgramado":"2026-11-20T07:00:00.000-06:00","finProgramado":"2026-11-20T14:30:00.000-06:00",
           "estadoProgramacion":"PROGRAMADA","capturada":true,"asistenciaId":"$ASISTENCIA_ID",
           "estadoOperativo":"PRESENTE","validacionAdministrativa":"PENDIENTE","revision":"1",
           "ultimaCaptura":{"eventoId":"$EVENTO_GRANDE","actorId":"${ContratoIam.USUARIO_ID}","registradoEn":"2026-11-20T07:04:12.345-06:00"},
           "capturable":false,"motivoNoCapturable":"YA_REGISTRADA","campoNuevoDelServidor":"se ignora"},
          {"programacionId":"$PROGRAMACION_PENDIENTE",
           "empleado":{"empleadoId":"7b2e0000-0000-4000-8000-0000000000a2","nombres":"Luis","primerApellido":"Ejemplo"},
           "horarioId":"$HORARIO_TM","turnoCodigo":"TM",
           "inicioProgramado":"2026-11-20T07:00:00.000-06:00","finProgramado":"2026-11-20T14:30:00.000-06:00",
           "estadoProgramacion":"PROGRAMADA","capturada":false,"asistenciaId":null,
           "estadoOperativo":"PENDIENTE","validacionAdministrativa":null,"revision":"0",
           "ultimaCaptura":null,"capturable":true,"motivoNoCapturable":null},
          {"programacionId":"$PROGRAMACION_CANCELADA",
           "empleado":{"empleadoId":"7b2e0000-0000-4000-8000-0000000000a3","nombres":"Marta","primerApellido":"Prueba"},
           "inicioProgramado":"2026-11-20T14:30:00.000-06:00","finProgramado":"2026-11-20T21:00:00.000-06:00",
           "estadoProgramacion":"CANCELADA","capturada":false,
           "estadoOperativo":"NO_EXIGIBLE","revision":"0",
           "capturable":false,"motivoNoCapturable":"PROGRAMACION_NO_CAPTURABLE"}
         ],
         "nextCursor":"$CURSOR","limit":100,"order":"id:asc"}
    """.trimIndent()

    val PAGINA_VACIA = """
        {"hospitalId":"${ContratoIam.HOSPITAL_ID}","servicioId":"${ContratoIam.SERVICIO_ID}","fecha":"$FECHA","zonaHoraria":"$ZONA",
         "items":[],"nextCursor":null,"limit":100,"order":"id:asc"}
    """.trimIndent()

    fun acuse(estado: String = "PRESENTE", programacionId: String = PROGRAMACION_PENDIENTE) = """
        {"programacionId":"$programacionId","asistenciaId":"$ASISTENCIA_ID","eventoId":"$EVENTO_GRANDE",
         "estadoOperativo":"$estado","validacionAdministrativa":"PENDIENTE","actorId":"${ContratoIam.USUARIO_ID}",
         "registradoEn":"2026-11-20T07:05:00.000-06:00","revision":"1"}
    """.trimIndent()
}

fun respuestaJson(codigo: Int, cuerpo: String, vararg encabezados: Pair<String, String>): MockResponse =
    MockResponse.Builder()
        .code(codigo)
        .addHeader("Content-Type", "application/json")
        .apply { encabezados.forEach { (nombre, valor) -> addHeader(nombre, valor) } }
        .body(Buffer().writeUtf8(cuerpo))
        .build()

fun retrofitPara(servidor: MockWebServer, cliente: OkHttpClient = OkHttpClient()): Retrofit = Retrofit.Builder()
    .baseUrl(servidor.url("/api/v1/"))
    .client(cliente)
    .addConverterFactory(SigehJson.asConverterFactory("application/json".toMediaType()))
    .build()
