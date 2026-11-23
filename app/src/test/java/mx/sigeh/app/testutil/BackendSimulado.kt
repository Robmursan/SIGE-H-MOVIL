package mx.sigeh.app.testutil

import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest

/**
 * Backend SIGE-H simulado **a nivel HTTP** (dispatcher de MockWebServer) para probar la app de punta
 * a punta mientras no existe la API QA. La cadena real de OkHttp (`AuthInterceptor`,
 * `SesionAuthenticator`, `TokenRefresher`), los clientes Retrofit y los repositorios remotos hablan
 * con él exactamente igual que con SIGE-H-WEB.
 *
 * Cubre las siete operaciones del alcance móvil del contrato `sigeh-v1` (revisión e2e-mvp-02.1,
 * SHA-256 a6fbe480…) y `GET /meta/version`, con las reglas de `apps/api` en el commit 9de9802:
 *
 * - **IAM.** Login con `ValidationPipe` (400 con lista de mensajes), 401 genérico y 429 por cuenta tras
 *   [intentosPorCuenta] fallos (`{statusCode, message}` sin `error`). Access de [segundosAcceso] s y
 *   refresh opaco de 64 caracteres **de un solo uso**: reutilizar uno ya consumido revoca la sesión
 *   (la familia) y responde 401. Logout 204 y la sesión deja de servir.
 * - **Contextos.** Solo contextos propios vigentes con nombres; cualquier query da 400.
 * - **Pase de lista.** Validación estricta del query (como `strictDto`), 403 sin `ASISTENCIA_CAPTURAR`
 *   ni `ASISTENCIA_VALIDAR` en el ámbito, 404 «Contexto no disponible», paginación `id:asc` con cursor
 *   exclusivo y `motivoNoCapturable` en el mismo orden que `noCaptureReason`.
 * - **Captura.** `Idempotency-Key` UUID v4 obligatoria, cuerpo exacto `{estado, expectedRevision:"0"}`,
 *   replay con la misma clave y el mismo comando → el **mismo** acuse; misma clave con otro comando →
 *   409 `IDEMPOTENCY_CONFLICT`; jornada no elegible → 409 con el motivo. `eventoId` y `revision` son
 *   BIGINT mayores que 2^53.
 * - **Banderas.** [iamHabilitado], [asistenciaHabilitada] y [contextosDisponibles] reproducen los 503
 *   del backend con sus textos exactos. [capturasInciertas] simula un resultado transaccional incierto:
 *   la captura **sí** se guarda pero se responde 503 «reintente con la misma clave».
 *
 * Identidades sintéticas (ninguna es real): [CAPTURISTA] (capturista Android), [ADMIN_WEB]
 * (administrador web, sin facultades de asistencia) y [CAPTURISTA_B] (otro hospital, para casos de
 * aislamiento). Todas usan [CONTRASENA].
 *
 * No sustituye la validación en QA efímero: ANDROID_QA_CHECKLIST.md advierte que «la suite mock/local
 * no basta» para declarar Android/PostgreSQL real PASS.
 */
class BackendSimulado : Dispatcher() {

    // --- Configuración que cambian las pruebas ------------------------------------------------------

    @Volatile var iamHabilitado = true

    @Volatile var asistenciaHabilitada = true

    @Volatile var contextosDisponibles = true

    /** Siguientes capturas que se guardan pero responden 503 «resultado incierto». */
    @Volatile var capturasInciertas = 0

    @Volatile var intentosPorCuenta = 5

    @Volatile var segundosAcceso = 300

    // --- Modelo ---------------------------------------------------------------------------------------

    data class Contexto(
        val assignmentId: String,
        val hospitalId: String,
        val divisionId: String?,
        val servicioId: String?,
        val tipo: String,
        val vigenteDesde: String,
        val vigenteHasta: String?,
        val permisos: List<String>,
    )

    data class Identidad(
        val identificador: String,
        val password: String,
        val usuarioId: String,
        val contextos: List<Contexto>,
    )

    data class Hospital(val id: String, val nombre: String, val zona: String)

    data class Division(val id: String, val nombre: String, val hospitalId: String)

    data class Servicio(val id: String, val nombre: String, val hospitalId: String, val divisionId: String?)

    /** Captura registrada en el «servidor». */
    data class Registro(
        val asistenciaId: String,
        val estado: String,
        val eventoId: String,
        val actorId: String,
        val registradoEn: String,
        val clave: String,
        val comando: String,
    )

    class Jornada(
        val programacionId: String,
        val hospitalId: String,
        val servicioId: String,
        val fecha: String,
        val empleadoId: String,
        val nombres: String,
        val primerApellido: String,
        val horarioId: String?,
        val turnoCodigo: String?,
        val inicio: String,
        val fin: String,
        val estadoProgramacion: String = "PROGRAMADA",
        val enVentana: Boolean = true,
        val contratoVigente: Boolean = true,
        val cerrada: Boolean = false,
    ) {
        var registro: Registro? = null
            internal set
    }

    /** Lo que llegó en cada `PUT …/asistencia`, para comprobar clave, cuerpo y bearer. */
    data class CapturaRecibida(
        val programacionId: String,
        val idempotencyKey: String?,
        val cuerpo: String?,
        val autorizacion: String?,
    )

    private class Sesion(val sessionId: String, val usuarioId: String) {
        var revocada = false
    }

    private class Acceso(val sesion: Sesion) {
        var vencido = false
    }

    private class Refresh(val sesion: Sesion) {
        var consumido = false
    }

    private val hospitales = mapOf(
        HOSPITAL_A to Hospital(HOSPITAL_A, "Hospital Sintético A", "America/Mexico_City"),
        HOSPITAL_B to Hospital(HOSPITAL_B, "Hospital Sintético B", "America/Tijuana"),
    )

    private val divisiones = mapOf(
        DIVISION_A to Division(DIVISION_A, "División Sintética de Medicina", HOSPITAL_A),
        DIVISION_B to Division(DIVISION_B, "División Sintética de Urgencias", HOSPITAL_B),
    )

    private val servicios = mapOf(
        SERVICIO_A to Servicio(SERVICIO_A, "Medicina Interna Sintética", HOSPITAL_A, DIVISION_A),
        SERVICIO_A2 to Servicio(SERVICIO_A2, "Pediatría Sintética", HOSPITAL_A, DIVISION_A),
        SERVICIO_B to Servicio(SERVICIO_B, "Urgencias Sintéticas", HOSPITAL_B, DIVISION_B),
    )

    private val identidades = listOf(
        Identidad(
            identificador = CAPTURISTA,
            password = CONTRASENA,
            usuarioId = USUARIO_CAPTURISTA,
            contextos = listOf(
                Contexto(ASIGNACION_1, HOSPITAL_A, DIVISION_A, SERVICIO_A, "TITULAR", DESDE, null, listOf(CAPTURAR)),
                // Encargo temporal de solo consulta: debe verse con vigencia y sin «Capturar».
                Contexto(ASIGNACION_2, HOSPITAL_A, DIVISION_A, SERVICIO_A2, "ENCARGADO", DESDE, ENCARGO_HASTA, listOf(VALIDAR)),
            ),
        ),
        Identidad(
            identificador = ADMIN_WEB,
            password = CONTRASENA,
            usuarioId = USUARIO_ADMIN_WEB,
            contextos = listOf(
                Contexto(ASIGNACION_3, HOSPITAL_A, null, null, "TITULAR", DESDE, null, listOf("CATALOGOS_ADMINISTRAR", "PERSONAL_CONSULTAR")),
            ),
        ),
        Identidad(
            identificador = CAPTURISTA_B,
            password = CONTRASENA,
            usuarioId = USUARIO_B,
            contextos = listOf(
                Contexto(ASIGNACION_4, HOSPITAL_B, DIVISION_B, SERVICIO_B, "TITULAR", DESDE, null, listOf(CAPTURAR)),
            ),
        ),
    )

    private val jornadas: Map<String, Jornada> = listOf(
        jornadaA(1, "Ana", "Sintética", HORARIO_MATUTINO, "MATUTINO", "13:00", "20:30"),
        jornadaA(2, "Bruno", "Prueba", HORARIO_MATUTINO, "MATUTINO", "13:00", "20:30"),
        jornadaA(3, "Carla", "Ejemplo", HORARIO_VESPERTINO, "VESPERTINO", "20:00", "23:59"),
        jornadaA(4, "Diego", "Ficticio", HORARIO_MATUTINO, "MATUTINO", "13:00", "20:30", estado = "CANCELADA"),
        jornadaA(5, "Elena", "Demo", HORARIO_VESPERTINO, "VESPERTINO", "20:00", "23:59", enVentana = false),
        jornadaA(6, "Fabián", "Muestra", HORARIO_MATUTINO, "MATUTINO", "13:00", "20:30", contratoVigente = false),
        Jornada(
            programacionId = PROGRAMACION_B,
            hospitalId = HOSPITAL_B,
            servicioId = SERVICIO_B,
            fecha = FECHA,
            empleadoId = "5e1a0000-0000-4000-8000-0000000e0b01",
            nombres = "Gabriela",
            primerApellido = "Otra",
            horarioId = null,
            turnoCodigo = null,
            inicio = "${FECHA}T15:00:00.000Z",
            fin = "${FECHA}T23:00:00.000Z",
        ),
    ).associateBy { it.programacionId }

    private val sesiones = mutableMapOf<String, Sesion>()
    private val accesos = mutableMapOf<String, Acceso>()
    private val refreshes = mutableMapOf<String, Refresh>()
    private val fallosPorCuenta = mutableMapOf<String, Int>()

    /** Inbox de idempotencia: (programacionId, actor, clave) → registro, como `REPLAY_SQL`. */
    private val porClave = mutableMapOf<Triple<String, String, String>, Registro>()

    private var siguienteEvento = BigInteger(EVENTO_INICIAL)
    private var instanteCaptura = Instant.parse("${FECHA}T13:05:00Z")
    private var contadorTokens = 0

    private val peticiones = mutableListOf<String>()
    private val capturas = mutableListOf<CapturaRecibida>()
    private var refreshRecibidos = 0
    private var replaysDetectados = 0
    private var logoutsRecibidos = 0
    private var tokenEnUrl = false

    // --- Consultas para las aserciones (sincronizadas: el dispatcher corre en hilos del servidor) -----

    @Synchronized fun peticiones(): List<String> = peticiones.toList()

    @Synchronized fun capturasRecibidas(): List<CapturaRecibida> = capturas.toList()

    @Synchronized fun refreshRecibidos(): Int = refreshRecibidos

    @Synchronized fun replaysDetectados(): Int = replaysDetectados

    @Synchronized fun logoutsRecibidos(): Int = logoutsRecibidos

    /** Algún token, refresh o encabezado `Bearer` viajó en la URL (nunca debe pasar). */
    @Synchronized fun tokenEnUrl(): Boolean = tokenEnUrl

    @Synchronized fun registroDe(programacionId: String): Registro? = jornadas[programacionId.lowercase()]?.registro

    /** Cuántas asistencias distintas existen (una por jornada como máximo, como el UNIQUE del backend). */
    @Synchronized fun asistenciasRegistradas(): Int = jornadas.values.count { it.registro != null }

    /** Simula que pasaron los 300 s: todo access token emitido deja de valer en el servidor. */
    @Synchronized fun vencerAccessTokens() {
        accesos.values.forEach { it.vencido = true }
    }

    /** Simula que Coordinación revocó la sesión desde otro dispositivo. */
    @Synchronized fun revocarSesiones() {
        sesiones.values.forEach { it.revocada = true }
    }

    // --- Despacho --------------------------------------------------------------------------------------

    @Synchronized
    override fun dispatch(request: RecordedRequest): MockResponse {
        val ruta = request.url.encodedPath
        peticiones += "${request.method} $ruta"
        val query = request.url.encodedQuery.orEmpty()
        if (query.contains("Bearer", ignoreCase = true) || query.contains("token", ignoreCase = true)) tokenEnUrl = true
        if (!ruta.startsWith(PREFIJO)) return error(404, "Cannot ${request.method} $ruta")
        val relativa = ruta.removePrefix(PREFIJO)
        val captura = RUTA_CAPTURA.matchEntire(relativa)
        return when {
            request.method == "GET" && relativa == "meta/version" -> version()
            relativa.startsWith("auth/") -> iam(request, relativa)
            request.method == "GET" && relativa == "pases-lista" -> pasesLista(request)
            request.method == "PUT" && captura != null -> capturar(request, captura.groupValues[1])
            else -> error(404, "Cannot ${request.method} $ruta")
        }
    }

    private fun version() = json(
        200,
        buildJsonObject {
            put("apiVersion", "1.0.0")
            put("apiBasePath", "/api/v1")
        },
        sinCache = false,
    )

    // --- IAM -----------------------------------------------------------------------------------------

    private fun iam(request: RecordedRequest, ruta: String): MockResponse {
        if (!iamHabilitado) return error(503, "IAM no disponible")
        return when {
            request.method == "POST" && ruta == "auth/login" -> login(request)
            request.method == "POST" && ruta == "auth/refresh" -> refresh(request)
            request.method == "GET" && ruta == "auth/me" -> conSesion(request) { perfil(it) }
            request.method == "GET" && ruta == "auth/me/contextos" -> conSesion(request) { contextos(request, it) }
            request.method == "POST" && ruta == "auth/logout" -> conSesion(request) { logout(it) }
            else -> error(404, "Cannot ${request.method} /api/v1/$ruta")
        }
    }

    private fun login(request: RecordedRequest): MockResponse {
        val cuerpo = objeto(request) ?: return errorLista(400, listOf("Entrada inválida"))
        val errores = (cuerpo.keys - setOf("identificador", "password")).map { "property $it should not exist" }.toMutableList()
        val identificador = texto(cuerpo["identificador"])
        val password = texto(cuerpo["password"])
        if (identificador == null || identificador.length !in 1..100 || !IDENTIFICADOR.matches(identificador)) {
            errores += "identificador must match the account format"
        }
        if (password == null || password.length !in 1..1024) errores += "password must be a non-empty string"
        if (errores.isNotEmpty() || identificador == null || password == null) return errorLista(400, errores)

        if ((fallosPorCuenta[identificador] ?: 0) >= intentosPorCuenta) return limite()
        val identidad = identidades.firstOrNull { it.identificador == identificador && it.password == password }
        if (identidad == null) {
            fallosPorCuenta[identificador] = (fallosPorCuenta[identificador] ?: 0) + 1
            return error(401, CREDENCIALES_INVALIDAS)
        }
        fallosPorCuenta.remove(identificador)
        val sesion = Sesion(nuevoUuid(), identidad.usuarioId)
        sesiones[sesion.sessionId] = sesion
        return json(200, emitirTokens(sesion))
    }

    private fun refresh(request: RecordedRequest): MockResponse {
        refreshRecibidos++
        val cuerpo = objeto(request) ?: return errorLista(400, listOf("Entrada inválida"))
        val token = texto(cuerpo["refreshToken"])
        if (cuerpo.keys != setOf("refreshToken") || token == null || !REFRESH.matches(token)) {
            return errorLista(400, listOf("refreshToken must be an opaque 64-character token"))
        }
        val registro = refreshes[token] ?: return error(401, CREDENCIALES_INVALIDAS)
        if (registro.consumido) {
            // Replay: el backend revoca toda la familia (la sesión) y responde 401 genérico.
            registro.sesion.revocada = true
            replaysDetectados++
            return error(401, CREDENCIALES_INVALIDAS)
        }
        if (registro.sesion.revocada) return error(401, CREDENCIALES_INVALIDAS)
        registro.consumido = true
        // Los access anteriores siguen valiendo hasta vencer (JWT), como en el backend real.
        return json(200, emitirTokens(registro.sesion))
    }

    private fun perfil(sesion: Sesion): MockResponse {
        val identidad = identidadDe(sesion)
        return json(
            200,
            buildJsonObject {
                put("usuarioId", identidad.usuarioId)
                put("sessionId", sesion.sessionId)
                put("provider", "local")
                putJsonArray("assignments") {
                    identidad.contextos.forEach { c ->
                        addJsonObject {
                            put("kind", c.tipo)
                            putJsonObject("scope") {
                                put("hospitalId", c.hospitalId)
                                put("divisionId", c.divisionId)
                                put("servicioId", c.servicioId)
                            }
                            putJsonArray("permissions") { c.permisos.forEach { add(it) } }
                        }
                    }
                }
            },
        )
    }

    private fun contextos(request: RecordedRequest, sesion: Sesion): MockResponse {
        if (request.url.queryParameterNames.isNotEmpty()) return error(400, "Contextos propios no admite filtros")
        if (!contextosDisponibles) return error(503, "Contextos no disponibles")
        val identidad = identidadDe(sesion)
        return json(
            200,
            buildJsonArray {
                identidad.contextos.sortedBy { it.assignmentId }.forEach { c ->
                    val hospital = hospitales.getValue(c.hospitalId)
                    val servicio = c.servicioId?.let { servicios.getValue(it) }
                    val divisionId = c.divisionId ?: servicio?.divisionId
                    addJsonObject {
                        put("assignmentId", c.assignmentId)
                        put("hospitalId", hospital.id)
                        put("hospitalNombre", hospital.nombre)
                        put("zonaHoraria", hospital.zona)
                        put("divisionId", divisionId)
                        put("divisionNombre", divisionId?.let { divisiones.getValue(it).nombre })
                        put("servicioId", servicio?.id)
                        put("servicioNombre", servicio?.nombre)
                        put("tipo", c.tipo)
                        put("vigenteDesde", c.vigenteDesde)
                        put("vigenteHasta", c.vigenteHasta)
                        putJsonArray("permisos") { c.permisos.sorted().forEach { add(it) } }
                    }
                }
            },
        )
    }

    private fun logout(sesion: Sesion): MockResponse {
        logoutsRecibidos++
        sesion.revocada = true
        return MockResponse.Builder()
            .code(204)
            .addHeader("Cache-Control", "no-store")
            .addHeader("Pragma", "no-cache")
            .build()
    }

    // --- Pase de lista ---------------------------------------------------------------------------------

    private fun pasesLista(request: RecordedRequest): MockResponse {
        // Orden de guards del backend: asistencia, IAM y sesión.
        if (!asistenciaHabilitada) return error(503, "Asistencia no habilitada")
        if (!iamHabilitado) return error(503, "IAM no disponible")
        val sesion = principal(request) ?: return error(401, CREDENCIALES_INVALIDAS)

        val url = request.url
        val nombres = url.queryParameterNames
        if (nombres.any { it !in PARAMETROS_PASE } || nombres.any { url.queryParameterValues(it).size != 1 }) {
            return error(400, "Entrada inválida")
        }
        val limiteTexto = url.queryParameter("limit")
        if (limiteTexto != null && !DIGITOS.matches(limiteTexto)) return error(400, "Límite inválido")
        val hospitalId = url.queryParameter("hospitalId")
        val servicioId = url.queryParameter("servicioId")
        val fecha = url.queryParameter("fecha")
        val horarioId = url.queryParameter("horarioId")
        val cursor = url.queryParameter("cursor")
        val limite = limiteTexto?.toIntOrNull() ?: 50
        if (hospitalId == null || !esUuid(hospitalId) ||
            servicioId == null || !esUuid(servicioId) ||
            fecha == null || !FECHA_TEXTO.matches(fecha) ||
            (horarioId != null && !esUuid(horarioId)) ||
            (cursor != null && !esUuid(cursor)) ||
            limite !in 1..100
        ) {
            return error(400, "Entrada inválida")
        }
        if (!fechaValida(fecha)) return error(400, "Fecha inválida")

        val usuarioId = sesion.usuarioId
        if (!permite(usuarioId, hospitalId, servicioId, CAPTURAR) && !permite(usuarioId, hospitalId, servicioId, VALIDAR)) {
            return error(403, "Acceso denegado")
        }
        val servicio = servicios[servicioId.lowercase()]
        if (servicio == null || servicio.hospitalId != hospitalId.lowercase()) return error(404, "Contexto no disponible")

        val puedeCapturar = permite(usuarioId, hospitalId, servicioId, CAPTURAR)
        val filas = jornadas.values
            .filter { it.servicioId == servicio.id && it.fecha == fecha }
            .filter { horarioId == null || it.horarioId == horarioId.lowercase() }
            .filter { cursor == null || it.programacionId > cursor.lowercase() }
            .sortedBy { it.programacionId }
        val pagina = filas.take(limite)
        val siguiente = if (filas.size > limite) pagina.last().programacionId else null
        return json(
            200,
            buildJsonObject {
                put("hospitalId", hospitalId)
                put("servicioId", servicioId)
                put("fecha", fecha)
                put("zonaHoraria", hospitales.getValue(servicio.hospitalId).zona)
                putJsonArray("items") { pagina.forEach { add(item(it, puedeCapturar)) } }
                put("nextCursor", siguiente)
                put("limit", limite)
                put("order", "id:asc")
            },
        )
    }

    private fun item(j: Jornada, puedeCapturar: Boolean): JsonObject {
        val r = j.registro
        val motivo = motivo(j, puedeCapturar)
        return buildJsonObject {
            put("programacionId", j.programacionId)
            putJsonObject("empleado") {
                put("empleadoId", j.empleadoId)
                put("nombres", j.nombres)
                put("primerApellido", j.primerApellido)
            }
            put("horarioId", j.horarioId)
            put("turnoCodigo", j.turnoCodigo)
            put("inicioProgramado", j.inicio)
            put("finProgramado", j.fin)
            put("estadoProgramacion", j.estadoProgramacion)
            put("capturada", r != null)
            put("asistenciaId", r?.asistenciaId)
            put("estadoOperativo", r?.estado ?: "PENDIENTE")
            put("validacionAdministrativa", if (r != null) "PENDIENTE" else null)
            put("revision", r?.eventoId ?: "0")
            if (r == null) {
                put("ultimaCaptura", JsonNull)
            } else {
                putJsonObject("ultimaCaptura") {
                    put("eventoId", r.eventoId)
                    put("actorId", r.actorId)
                    put("registradoEn", r.registradoEn)
                }
            }
            put("capturable", motivo == null)
            put("motivoNoCapturable", motivo)
        }
    }

    /** Mismo orden que `noCaptureReason` de `asistencia.repository.ts`. */
    private fun motivo(j: Jornada, puedeCapturar: Boolean): String? = when {
        j.registro != null -> "YA_REGISTRADA"
        !j.enVentana -> "FUERA_VENTANA"
        j.estadoProgramacion != "PROGRAMADA" -> "PROGRAMACION_NO_CAPTURABLE"
        !j.contratoVigente -> "CONTRATO_NO_VIGENTE"
        j.cerrada -> "CIERRE_EXISTENTE"
        !puedeCapturar -> "SIN_FACULTAD_CAPTURA"
        else -> null
    }

    // --- Captura ---------------------------------------------------------------------------------------

    private fun capturar(request: RecordedRequest, programacionIdCrudo: String): MockResponse {
        val clave = request.headers["Idempotency-Key"]
        capturas += CapturaRecibida(programacionIdCrudo, clave, request.body?.utf8(), request.headers["Authorization"])
        if (!asistenciaHabilitada) return error(503, "Asistencia no habilitada")
        if (!iamHabilitado) return error(503, "IAM no disponible")
        val sesion = principal(request) ?: return error(401, CREDENCIALES_INVALIDAS)

        // ValidationPipe global (whitelist + forbidNonWhitelisted) y después strictDto.
        val cuerpo = objeto(request) ?: return error(400, "Entrada inválida")
        val estado = texto(cuerpo["estado"])
        val revision = texto(cuerpo["expectedRevision"])
        if (cuerpo.keys != setOf("estado", "expectedRevision") || estado == null || estado !in ESTADOS_CAPTURA || revision != "0") {
            return error(400, "Entrada inválida")
        }
        if (!esUuid(programacionIdCrudo) || clave == null || !esUuidV4(clave)) {
            return error(400, "Identificador o clave inválidos")
        }

        val usuarioId = sesion.usuarioId
        val identidad = identidadDe(sesion)
        if (identidad.contextos.none { CAPTURAR in it.permisos }) return error(403, "Acceso denegado")
        val jornada = jornadas[programacionIdCrudo.lowercase()]
        if (jornada == null || !permite(usuarioId, jornada.hospitalId, jornada.servicioId, CAPTURAR)) {
            return error(404, "Programación no disponible")
        }

        val comando = listOf(usuarioId, jornada.programacionId, estado, revision).joinToString("|")
        val llave = Triple(jornada.programacionId, usuarioId, clave.lowercase())
        porClave[llave]?.let { previo ->
            return if (previo.comando == comando) json(200, acuse(jornada, previo)) else error(409, "IDEMPOTENCY_CONFLICT")
        }
        motivo(jornada, puedeCapturar = true)?.let { return error(409, it) }

        val evento = siguienteEvento.toString()
        siguienteEvento = siguienteEvento.add(BigInteger.ONE)
        instanteCaptura = instanteCaptura.plusSeconds(1)
        val registro = Registro(
            asistenciaId = nuevoUuid(),
            estado = estado,
            eventoId = evento,
            actorId = usuarioId,
            registradoEn = instanteCaptura.toString(),
            clave = clave.lowercase(),
            comando = comando,
        )
        jornada.registro = registro
        porClave[llave] = registro
        if (capturasInciertas > 0) {
            capturasInciertas--
            return error(503, "Asistencia no disponible; reintente con la misma clave")
        }
        return json(200, acuse(jornada, registro))
    }

    private fun acuse(j: Jornada, r: Registro) = buildJsonObject {
        put("programacionId", j.programacionId)
        put("asistenciaId", r.asistenciaId)
        put("eventoId", r.eventoId)
        put("estadoOperativo", r.estado)
        put("validacionAdministrativa", "PENDIENTE")
        put("actorId", r.actorId)
        put("registradoEn", r.registradoEn)
        put("revision", r.eventoId)
    }

    // --- Autorización ----------------------------------------------------------------------------------

    private inline fun conSesion(request: RecordedRequest, accion: (Sesion) -> MockResponse): MockResponse {
        val sesion = principal(request) ?: return error(401, CREDENCIALES_INVALIDAS)
        return accion(sesion)
    }

    private fun principal(request: RecordedRequest): Sesion? {
        val encabezado = request.headers["Authorization"] ?: return null
        if (!encabezado.startsWith("Bearer ")) return null
        val acceso = accesos[encabezado.removePrefix("Bearer ")] ?: return null
        if (acceso.vencido || acceso.sesion.revocada) return null
        return acceso.sesion
    }

    private fun identidadDe(sesion: Sesion): Identidad = identidades.single { it.usuarioId == sesion.usuarioId }

    /**
     * Como `assignmentAllows`: el permiso debe estar en un contexto del mismo hospital que cubra el
     * servicio (contexto de hospital, de su división o del servicio mismo).
     */
    private fun permite(usuarioId: String, hospitalId: String, servicioId: String, permiso: String): Boolean {
        val servicio = servicios[servicioId.lowercase()]
        val identidad = identidades.single { it.usuarioId == usuarioId }
        return identidad.contextos.any { c ->
            permiso in c.permisos &&
                c.hospitalId == hospitalId.lowercase() &&
                (c.servicioId == null || c.servicioId == servicioId.lowercase()) &&
                (c.divisionId == null || c.divisionId == servicio?.divisionId)
        }
    }

    private fun emitirTokens(sesion: Sesion): JsonObject {
        contadorTokens++
        val acceso = "acc-$contadorTokens-${UUID.randomUUID()}"
        val refresh = (UUID.randomUUID().toString() + UUID.randomUUID().toString()).replace("-", "")
        accesos[acceso] = Acceso(sesion)
        refreshes[refresh] = Refresh(sesion)
        return buildJsonObject {
            put("accessToken", acceso)
            put("refreshToken", refresh)
            put("tokenType", "Bearer")
            put("expiresIn", segundosAcceso)
            put("sessionId", sesion.sessionId)
        }
    }

    // --- Respuestas ------------------------------------------------------------------------------------

    private fun json(codigo: Int, cuerpo: JsonElement, sinCache: Boolean = true): MockResponse =
        MockResponse.Builder()
            .code(codigo)
            .addHeader("Content-Type", "application/json; charset=utf-8")
            .apply {
                if (sinCache) {
                    addHeader("Cache-Control", "no-store")
                    addHeader("Pragma", "no-cache")
                }
            }
            .body(cuerpo.toString())
            .build()

    /** Formato por defecto de NestJS: `ReadHttpErrorDto {statusCode, message, error}`. */
    private fun error(codigo: Int, mensaje: String): MockResponse = json(
        codigo,
        buildJsonObject {
            put("statusCode", codigo)
            put("message", mensaje)
            put("error", ETIQUETAS.getValue(codigo))
        },
    )

    /** Errores del `ValidationPipe` global: `message` es una lista. */
    private fun errorLista(codigo: Int, mensajes: List<String>): MockResponse = json(
        codigo,
        buildJsonObject {
            put("statusCode", codigo)
            putJsonArray("message") { mensajes.forEach { add(it) } }
            put("error", ETIQUETAS.getValue(codigo))
        },
    )

    /** `IamRateLimitErrorDto`: `HttpException(string, 429)` no lleva `error` ni `Retry-After`. */
    private fun limite(): MockResponse = json(
        429,
        buildJsonObject {
            put("statusCode", 429)
            put("message", "Demasiados intentos")
        },
    )

    private fun objeto(request: RecordedRequest): JsonObject? {
        val texto = request.body?.utf8()?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { Json.parseToJsonElement(texto) as? JsonObject }.getOrNull()
    }

    private fun texto(elemento: JsonElement?): String? =
        (elemento as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun fechaValida(fecha: String): Boolean = try {
        LocalDate.parse(fecha).year >= 1
    } catch (_: DateTimeParseException) {
        false
    }

    private fun nuevoUuid(): String = UUID.randomUUID().toString()

    private fun jornadaA(
        n: Int,
        nombres: String,
        apellido: String,
        horarioId: String,
        turno: String,
        inicioUtc: String,
        finUtc: String,
        estado: String = "PROGRAMADA",
        enVentana: Boolean = true,
        contratoVigente: Boolean = true,
    ) = Jornada(
        programacionId = programacionA(n),
        hospitalId = HOSPITAL_A,
        servicioId = SERVICIO_A,
        fecha = FECHA,
        empleadoId = "5e1a0000-0000-4000-8000-0000000e0a%02d".format(n),
        nombres = nombres,
        primerApellido = apellido,
        horarioId = horarioId,
        turnoCodigo = turno,
        inicio = "${FECHA}T$inicioUtc:00.000Z",
        fin = "${FECHA}T$finUtc:00.000Z",
        estadoProgramacion = estado,
        enVentana = enVentana,
        contratoVigente = contratoVigente,
    )

    companion object {
        const val PREFIJO = "/api/v1/"

        /** Fecha local de las jornadas sintéticas (zona del Hospital A, `America/Mexico_City`). */
        const val FECHA = "2026-11-23"

        const val CAPTURISTA = "capturista_android_sintetico"
        const val ADMIN_WEB = "admin_web_sintetico"
        const val CAPTURISTA_B = "capturista_b_sintetico"
        const val CONTRASENA = "Contrasena-Sintetica-2026"

        const val USUARIO_CAPTURISTA = "5e1a0000-0000-4000-8000-0000000000e1"
        const val USUARIO_ADMIN_WEB = "5e1a0000-0000-4000-8000-0000000000e2"
        const val USUARIO_B = "5e1a0000-0000-4000-8000-0000000000e3"

        const val HOSPITAL_A = "5e1a0000-0000-4000-8000-00000000a001"
        const val HOSPITAL_B = "5e1a0000-0000-4000-8000-00000000b001"
        const val DIVISION_A = "5e1a0000-0000-4000-8000-00000000d0a1"
        const val DIVISION_B = "5e1a0000-0000-4000-8000-00000000d0b1"
        const val SERVICIO_A = "5e1a0000-0000-4000-8000-00000000c0a1"
        const val SERVICIO_A2 = "5e1a0000-0000-4000-8000-00000000c0a2"
        const val SERVICIO_B = "5e1a0000-0000-4000-8000-00000000c0b1"
        const val HORARIO_MATUTINO = "5e1a0000-0000-4000-8000-00000000f001"
        const val HORARIO_VESPERTINO = "5e1a0000-0000-4000-8000-00000000f002"

        const val ASIGNACION_1 = "5e1a0000-0000-4000-8000-0000000a5001"
        const val ASIGNACION_2 = "5e1a0000-0000-4000-8000-0000000a5002"
        const val ASIGNACION_3 = "5e1a0000-0000-4000-8000-0000000a5003"
        const val ASIGNACION_4 = "5e1a0000-0000-4000-8000-0000000a5004"

        const val DESDE = "2026-11-01T06:00:00.000Z"
        const val ENCARGO_HASTA = "2026-12-01T06:00:00.000Z"

        const val PROGRAMACION_B = "7c000000-0000-4000-8000-000000000101"

        /** 2^53 + 1: se perdería al leerlo como número de punto flotante. */
        const val EVENTO_INICIAL = "9007199254740993"

        const val CREDENCIALES_INVALIDAS = "Credenciales o sesión inválidas"

        const val CAPTURAR = "ASISTENCIA_CAPTURAR"
        const val VALIDAR = "ASISTENCIA_VALIDAR"

        /** Programaciones del Servicio A en orden `id:asc`: 1–3 capturables, 4 cancelada, 5 fuera de ventana, 6 sin contrato. */
        fun programacionA(n: Int): String = "7c000000-0000-4000-8000-%012d".format(n)

        private val RUTA_CAPTURA = Regex("^programaciones/([^/]+)/asistencia$")
        private val IDENTIFICADOR = Regex("""^(?!.*\p{Cc})\S(?:.*\S)?$""")
        private val REFRESH = Regex("^[A-Za-z0-9_-]{64}$")
        private val DIGITOS = Regex("^[0-9]+$")
        private val FECHA_TEXTO = Regex("""^\d{4}-\d{2}-\d{2}$""")
        private val UUID_CUALQUIERA = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        private val UUID_V4 = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
        private val PARAMETROS_PASE = setOf("hospitalId", "servicioId", "fecha", "horarioId", "cursor", "limit")
        private val ESTADOS_CAPTURA = setOf("PRESENTE", "AUSENTE", "PARCIAL")
        private val ETIQUETAS = mapOf(
            400 to "Bad Request",
            401 to "Unauthorized",
            403 to "Forbidden",
            404 to "Not Found",
            409 to "Conflict",
            503 to "Service Unavailable",
        )

        private fun esUuid(texto: String) = UUID_CUALQUIERA.matches(texto)

        private fun esUuidV4(texto: String) = UUID_V4.matches(texto)
    }
}
