package mx.sigeh.app.core.network

import java.math.BigInteger
import javax.inject.Provider
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockWebServer
import mx.sigeh.app.core.auth.PoliticaAmbito
import mx.sigeh.app.core.common.HoraHospital
import mx.sigeh.app.core.data.MotivoRechazo
import mx.sigeh.app.core.data.ResultadoCaptura
import mx.sigeh.app.core.data.ResultadoConsulta
import mx.sigeh.app.core.data.ResultadoContextos
import mx.sigeh.app.core.data.ResultadoLogin
import mx.sigeh.app.core.data.remote.IamAuthRepository
import mx.sigeh.app.core.data.remote.RemotoContextosRepository
import mx.sigeh.app.core.data.remote.RemotoPaseListaRepository
import mx.sigeh.app.core.model.EstadoCaptura
import mx.sigeh.app.core.model.MotivoNoCapturable
import mx.sigeh.app.core.model.SigehJson
import mx.sigeh.app.core.model.ValidacionAdministrativa
import mx.sigeh.app.core.network.asistencia.AsistenciaApi
import mx.sigeh.app.core.network.iam.IamApi
import mx.sigeh.app.core.network.iam.OperacionesApi
import mx.sigeh.app.core.network.iam.RefreshRequest
import mx.sigeh.app.testutil.BackendSimulado
import mx.sigeh.app.testutil.RelojFijo
import mx.sigeh.app.testutil.SessionStoreEnMemoria
import mx.sigeh.app.testutil.retrofitPara
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Recorrido del alcance móvil (autenticación, contextos, pase de lista y captura) contra
 * [BackendSimulado], que reproduce las reglas de SIGE-H-WEB 9de9802 mientras no hay API QA.
 *
 * Usa la cadena real de OkHttp en el orden de `NetworkModule` y los repositorios remotos reales: es
 * lo mismo que hablará con el servidor QA. Cubre los puntos de `ANDROID_QA_CHECKLIST.md` que se
 * pueden comprobar sin backend; no sustituye la evidencia en QA efímero.
 */
class FlujoMovilContraBackendSimuladoTest {

    private val backend = BackendSimulado()
    private val servidor = MockWebServer()
    private val reloj = RelojFijo(HoraHospital.instante("2026-11-23T14:00:00Z")!!)
    private val sesiones = SessionStoreEnMemoria(null)

    private lateinit var iamApi: IamApi
    private lateinit var asistenciaApi: AsistenciaApi
    private lateinit var auth: IamAuthRepository
    private lateinit var contextos: RemotoContextosRepository
    private lateinit var pase: RemotoPaseListaRepository

    @Before
    fun iniciar() {
        servidor.dispatcher = backend
        servidor.start()
        val refresher = TokenRefresher(sesiones, Provider { iamApi }, reloj)
        val cliente = OkHttpClient.Builder()
            .addInterceptor(RequestIdInterceptor())
            .addInterceptor(IdempotencyKeyInterceptor())
            .addInterceptor(AuthInterceptor(refresher))
            .authenticator(SesionAuthenticator(refresher))
            .build()
        val retrofit = retrofitPara(servidor, cliente)
        iamApi = retrofit.create(IamApi::class.java)
        asistenciaApi = retrofit.create(AsistenciaApi::class.java)
        auth = IamAuthRepository(iamApi, reloj)
        contextos = RemotoContextosRepository(iamApi)
        pase = RemotoPaseListaRepository(asistenciaApi, ApiErrorParser(SigehJson))
    }

    @After
    fun detener() = servidor.close()

    private suspend fun entrar(identificador: String = BackendSimulado.CAPTURISTA) {
        val resultado = auth.iniciarSesion(identificador, BackendSimulado.CONTRASENA)
        sesiones.guardar((resultado as ResultadoLogin.Exito).sesion)
    }

    private suspend fun consultarServicioA(): ResultadoConsulta =
        pase.consultar(BackendSimulado.HOSPITAL_A, BackendSimulado.SERVICIO_A, BackendSimulado.FECHA)

    @Test
    fun recorrido_loginContextosPaseCapturaRecargaYLogout() = runTest {
        entrar()
        assertEquals(BackendSimulado.USUARIO_CAPTURISTA, sesiones.actual()!!.usuarioId)
        assertEquals(reloj.ahora + 300_000, sesiones.actual()!!.accessExpiraEnMillis)

        // Contextos → ámbitos del selector: Medicina Interna para capturar y un encargo de solo consulta.
        val propios = (contextos.contextosPropios() as ResultadoContextos.Exito).contextos
        val ambitos = PoliticaAmbito.ambitosDeContextos(propios)
        assertEquals(listOf("Medicina Interna Sintética", "Pediatría Sintética"), ambitos.map { it.servicioNombre })
        val capturable = ambitos.single { it.facultades.capturar }
        assertEquals("America/Mexico_City", capturable.zonaHoraria)
        assertNull(capturable.vigenteHasta)
        val encargo = ambitos.single { !it.facultades.capturar }
        assertTrue(encargo.facultades.validar)
        assertEquals(BackendSimulado.ENCARGO_HASTA, encargo.vigenteHasta)

        // Pase: el servidor decide qué se puede capturar y por qué no.
        val pagina = (consultarServicioA() as ResultadoConsulta.Exito).pagina
        assertEquals(6, pagina.jornadas.size)
        assertNull(pagina.siguienteCursor)
        assertEquals(
            listOf(null, null, null, MotivoNoCapturable.PROGRAMACION_NO_CAPTURABLE, MotivoNoCapturable.FUERA_VENTANA, MotivoNoCapturable.CONTRATO_NO_VIGENTE),
            pagina.jornadas.map { it.motivoNoCapturable },
        )
        val jornada = pagina.jornadas.first()
        assertFalse(jornada.capturada)
        assertEquals("0", jornada.revision)

        // Captura: bearer, Idempotency-Key v4 y cuerpo exacto de primera captura.
        val clave = "3f2c9e1a-6b7d-4c1e-9a2b-00000000c1a7"
        val acuse = (pase.capturar(jornada.programacionId, EstadoCaptura.PRESENTE, clave) as ResultadoCaptura.Confirmada).acuse
        assertEquals(ValidacionAdministrativa.PENDIENTE, acuse.validacion)
        assertTrue("BIGINT mayor que 2^53, intacto como texto", BigInteger(acuse.eventoId) > BigInteger.valueOf(2).pow(53))
        assertEquals(BackendSimulado.EVENTO_INICIAL, acuse.eventoId)
        val recibida = backend.capturasRecibidas().single()
        assertEquals(clave, recibida.idempotencyKey)
        assertTrue(recibida.autorizacion!!.startsWith("Bearer "))
        val cuerpo = SigehJson.parseToJsonElement(recibida.cuerpo!!).jsonObject
        assertEquals(setOf("estado", "expectedRevision"), cuerpo.keys)
        assertEquals("PRESENTE", cuerpo["estado"]!!.jsonPrimitive.content)
        assertEquals("0", cuerpo["expectedRevision"]!!.jsonPrimitive.content)

        // Recarga: la jornada aparece registrada con la revisión del acuse.
        val recargada = (consultarServicioA() as ResultadoConsulta.Exito).pagina.jornadas.first { it.programacionId == jornada.programacionId }
        assertTrue(recargada.capturada)
        assertEquals(MotivoNoCapturable.YA_REGISTRADA, recargada.motivoNoCapturable)
        assertEquals(acuse.eventoId, recargada.revision)
        assertEquals(acuse.eventoId, recargada.ultimaCaptura!!.eventoId)

        // Logout 204; la app purga la sesión local y no reintenta con la descartada.
        auth.cerrarSesion()
        assertEquals(1, backend.logoutsRecibidos())
        sesiones.limpiar()
        val trasLogout = contextos.contextosPropios()
        assertEquals(ResultadoContextos.SesionVencida, trasLogout)
        assertEquals("Sin sesión no se intenta ningún refresh", 0, backend.refreshRecibidos())
        assertFalse(backend.tokenEnUrl())
    }

    @Test
    fun accessVencidoEnElServidor_dosPeticionesSimultaneas_unSoloRefresh() = runTest {
        entrar()
        val refreshInicial = sesiones.actual()!!.refreshToken
        backend.vencerAccessTokens()

        coroutineScope {
            listOf(
                async { contextos.contextosPropios() },
                async { consultarServicioA() },
                async { iamApi.perfil(null).usuarioId },
            ).awaitAll()
        }.let { resultados ->
            assertTrue(resultados[0] is ResultadoContextos.Exito)
            assertTrue(resultados[1] is ResultadoConsulta.Exito)
            assertEquals(BackendSimulado.USUARIO_CAPTURISTA, resultados[2])
        }

        assertEquals("Single-flight: un replay revocaría la familia", 1, backend.refreshRecibidos())
        assertEquals(0, backend.replaysDetectados())
        assertNotEquals("El refresh rota", refreshInicial, sesiones.actual()!!.refreshToken)
        assertFalse(sesiones.actual()!!.vencida)
    }

    @Test
    fun refreshReutilizado_revocaLaFamiliaYLaAppPideLogin() = runTest {
        entrar()
        val refreshUsado = sesiones.actual()!!.refreshToken!!
        backend.vencerAccessTokens()
        assertTrue(contextos.contextosPropios() is ResultadoContextos.Exito)
        assertEquals(1, backend.refreshRecibidos())

        // Alguien reenvía el refresh ya consumido (por ejemplo, una copia robada): replay.
        val replay = iamApi.refrescar(RefreshRequest(refreshUsado)).execute()
        assertEquals(401, replay.code())
        assertEquals(1, backend.replaysDetectados())

        // La sesión de la app quedó revocada: el siguiente 401 intenta una sola renovación y termina.
        assertEquals(ResultadoContextos.SesionVencida, contextos.contextosPropios())
        assertTrue(sesiones.actual()!!.vencida)
        assertEquals(3, backend.refreshRecibidos())
    }

    @Test
    fun capturaIncierta503_reintentoConLaMismaClave_mismoAcuseYUnSoloRegistro() = runTest {
        entrar()
        val programacion = BackendSimulado.programacionA(2)
        val clave = "7d1b5a90-2c3e-4f6a-8b9c-0000000003e1"
        backend.capturasInciertas = 1

        assertEquals(ResultadoCaptura.Incierta, pase.capturar(programacion, EstadoCaptura.AUSENTE, clave))
        val confirmada = pase.capturar(programacion, EstadoCaptura.AUSENTE, clave) as ResultadoCaptura.Confirmada

        assertEquals(backend.registroDe(programacion)!!.eventoId, confirmada.acuse.eventoId)
        assertEquals(EstadoCaptura.AUSENTE, confirmada.acuse.estado)
        assertEquals(1, backend.asistenciasRegistradas())
        assertEquals(listOf(clave, clave), backend.capturasRecibidas().map { it.idempotencyKey })
    }

    @Test
    fun mismaClaveConOtroEstado_409ConflictoDeIdempotencia() = runTest {
        entrar()
        val programacion = BackendSimulado.programacionA(1)
        val clave = "0c8f6a3e-1d2b-4e5f-a6b7-0000000009f1"
        assertTrue(pase.capturar(programacion, EstadoCaptura.PRESENTE, clave) is ResultadoCaptura.Confirmada)

        val otra = pase.capturar(programacion, EstadoCaptura.PARCIAL, clave)

        assertEquals(ResultadoCaptura.Rechazada(MotivoRechazo.CONFLICTO_IDEMPOTENCIA), otra)
        assertEquals("PRESENTE", backend.registroDe(programacion)!!.estado)
    }

    @Test
    fun otraClaveSobreJornadaRegistrada_409YaRegistrada() = runTest {
        entrar()
        val programacion = BackendSimulado.programacionA(3)
        assertTrue(pase.capturar(programacion, EstadoCaptura.PRESENTE, "11111111-2222-4333-8444-555555555555") is ResultadoCaptura.Confirmada)

        val segunda = pase.capturar(programacion, EstadoCaptura.PRESENTE, "66666666-7777-4888-9999-aaaaaaaaaaaa")

        assertEquals(ResultadoCaptura.Rechazada(MotivoRechazo.YA_REGISTRADA), segunda)
        assertEquals(1, backend.asistenciasRegistradas())
    }

    @Test
    fun jornadasNoElegibles_409ConElMotivoDelServidor() = runTest {
        entrar()

        assertEquals(
            ResultadoCaptura.Rechazada(MotivoRechazo.PROGRAMACION_NO_CAPTURABLE),
            pase.capturar(BackendSimulado.programacionA(4), EstadoCaptura.PRESENTE, "a1a1a1a1-b2b2-4c3c-8d4d-e5e5e5e5e5e5"),
        )
        assertEquals(
            ResultadoCaptura.Rechazada(MotivoRechazo.FUERA_VENTANA),
            pase.capturar(BackendSimulado.programacionA(5), EstadoCaptura.PRESENTE, "b1b1b1b1-c2c2-4d3d-9e4e-f5f5f5f5f5f5"),
        )
        assertEquals(
            ResultadoCaptura.Rechazada(MotivoRechazo.CONTRATO_NO_VIGENTE),
            pase.capturar(BackendSimulado.programacionA(6), EstadoCaptura.PRESENTE, "c1c1c1c1-d2d2-4e3e-a4f4-a5a5a5a5a5a5"),
        )
        assertEquals(0, backend.asistenciasRegistradas())
    }

    @Test
    fun claveQueNoEsUuidV4_400SinRegistrar() = runTest {
        entrar()

        val resultado = pase.capturar(BackendSimulado.programacionA(1), EstadoCaptura.PRESENTE, "no-es-uuid")

        assertEquals(ResultadoCaptura.Rechazada(MotivoRechazo.SOLICITUD_INVALIDA), resultado)
        assertEquals(0, backend.asistenciasRegistradas())
    }

    @Test
    fun usuarioDeOtroHospital_noLeeNiCapturaElServicioA() = runTest {
        entrar(BackendSimulado.CAPTURISTA_B)

        assertEquals(ResultadoConsulta.SinPermiso, consultarServicioA())
        assertEquals(
            ResultadoCaptura.NoEncontrada,
            pase.capturar(BackendSimulado.programacionA(1), EstadoCaptura.PRESENTE, "d4d4d4d4-e5e5-4f6f-8a7a-b8b8b8b8b8b8"),
        )
        assertEquals(0, backend.asistenciasRegistradas())

        // Su propio servicio sí, en la zona de su hospital.
        val propia = pase.consultar(BackendSimulado.HOSPITAL_B, BackendSimulado.SERVICIO_B, BackendSimulado.FECHA)
        assertEquals("America/Tijuana", (propia as ResultadoConsulta.Exito).pagina.zonaHoraria)
    }

    @Test
    fun administradorWeb_entraPeroNoTieneAmbitosDeAsistencia() = runTest {
        entrar(BackendSimulado.ADMIN_WEB)

        val propios = (contextos.contextosPropios() as ResultadoContextos.Exito).contextos
        assertEquals(1, propios.size)
        assertTrue("El selector no le ofrece nada", PoliticaAmbito.ambitosDeContextos(propios).isEmpty())
        assertEquals(ResultadoConsulta.SinPermiso, consultarServicioA())
        assertEquals(
            ResultadoCaptura.SinPermiso,
            pase.capturar(BackendSimulado.programacionA(1), EstadoCaptura.PRESENTE, "e5e5e5e5-f6f6-4a7a-9b8b-c9c9c9c9c9c9"),
        )
    }

    @Test
    fun paginacion_cursorExclusivoIdAsc_recorreTodasLasPaginas() = runTest {
        entrar()
        val vistos = mutableListOf<String>()
        var cursor: String? = null
        var paginas = 0
        do {
            val respuesta = asistenciaApi.pasesLista(BackendSimulado.HOSPITAL_A, BackendSimulado.SERVICIO_A, BackendSimulado.FECHA, null, cursor, 4)
            assertEquals("id:asc", respuesta.order)
            vistos += respuesta.items.map { it.programacionId }
            cursor = respuesta.nextCursor
            paginas++
        } while (cursor != null && paginas < 10)

        assertEquals(2, paginas)
        assertEquals((1..6).map { BackendSimulado.programacionA(it) }, vistos)
    }

    @Test
    fun filtroPorHorario_soloEsasJornadas() = runTest {
        entrar()

        val pagina = (pase.consultar(BackendSimulado.HOSPITAL_A, BackendSimulado.SERVICIO_A, BackendSimulado.FECHA, BackendSimulado.HORARIO_VESPERTINO) as ResultadoConsulta.Exito).pagina

        assertEquals(listOf(3, 5).map { BackendSimulado.programacionA(it) }, pagina.jornadas.map { it.programacionId })
    }

    @Test
    fun banderasApagadas_503ConSusTextos() = runTest {
        entrar()
        backend.contextosDisponibles = false
        assertEquals(ResultadoContextos.NoDisponible(503), contextos.contextosPropios())

        backend.asistenciaHabilitada = false
        assertEquals(ResultadoConsulta.NoHabilitada, consultarServicioA())
        assertEquals(
            ResultadoCaptura.NoHabilitada,
            pase.capturar(BackendSimulado.programacionA(1), EstadoCaptura.PRESENTE, "f6f6f6f6-a7a7-4b8b-8c9c-d0d0d0d0d0d0"),
        )

        backend.iamHabilitado = false
        assertEquals(ResultadoLogin.ServicioNoDisponible, auth.iniciarSesion(BackendSimulado.CAPTURISTA, BackendSimulado.CONTRASENA))
    }

    @Test
    fun credencialesIncorrectas_401Generico_yLimite429SinRetryAfter() = runTest {
        repeat(5) {
            assertEquals(ResultadoLogin.CredencialesInvalidas, auth.iniciarSesion(BackendSimulado.CAPTURISTA, "incorrecta"))
        }

        val bloqueado = auth.iniciarSesion(BackendSimulado.CAPTURISTA, BackendSimulado.CONTRASENA)

        assertEquals(ResultadoLogin.LimiteIntentos(ApiErrorParser.ESPERA_POR_DEFECTO_S), bloqueado)
        assertNull(sesiones.actual())
    }

    @Test
    fun meta_version_esPublica() = runTest {
        val version = retrofitPara(servidor).create(OperacionesApi::class.java).version()

        assertEquals("1.0.0", version.apiVersion)
        assertEquals("/api/v1", version.apiBasePath)
    }
}
