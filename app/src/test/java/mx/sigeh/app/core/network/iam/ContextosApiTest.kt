package mx.sigeh.app.core.network.iam

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import mx.sigeh.app.core.data.ResultadoContextos
import mx.sigeh.app.core.data.remote.RemotoContextosRepository
import mx.sigeh.app.core.model.RolAlcance
import mx.sigeh.app.core.network.ApiErrorParser
import mx.sigeh.app.core.network.CausaErrorApi
import mx.sigeh.app.core.model.SigehJson
import mx.sigeh.app.testutil.ClienteDePrueba
import mx.sigeh.app.testutil.ContratoIam
import mx.sigeh.app.testutil.RelojFijo
import mx.sigeh.app.testutil.respuestaJson
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * `GET /auth/me/contextos` (operationId `iamOwnContexts`, nuevo en el contrato e2e-mvp-02.1) con la
 * cadena real de OkHttp de la app contra respuestas con la forma exacta de `OwnContextDto`.
 */
class ContextosApiTest {

    private val servidor = MockWebServer()
    private val reloj = RelojFijo(5_000_000L)
    private lateinit var cliente: ClienteDePrueba
    private lateinit var api: IamApi

    @Before
    fun iniciar() {
        servidor.start()
        cliente = ClienteDePrueba(servidor, reloj)
        api = cliente.crear()
    }

    @After
    fun detener() = servidor.close()

    @Test
    fun contextos_llevaBearerSinParametros_yLeeNulosYCamposNuevos() = runTest {
        servidor.enqueue(respuestaJson(200, ContratoIam.CONTEXTOS))

        val contextos = api.contextos()

        val peticion = servidor.takeRequest()
        assertEquals("GET", peticion.method)
        assertEquals("/api/v1/auth/me/contextos", peticion.url.encodedPath)
        assertNull("El backend responde 400 a cualquier filtro", peticion.url.encodedQuery)
        assertEquals("Bearer acceso-1", peticion.headers["Authorization"])

        assertEquals(3, contextos.size)
        val titular = contextos[0]
        assertEquals(ContratoIam.ASIGNACION_SERVICIO, titular.assignmentId)
        assertEquals("Hospital Sintético A", titular.hospitalNombre)
        assertEquals("America/Mexico_City", titular.zonaHoraria)
        assertEquals(ContratoIam.SERVICIO_ID, titular.servicioId)
        assertEquals("Servicio Sintético", titular.servicioNombre)
        assertEquals("TITULAR", titular.tipo)
        assertNull(titular.vigenteHasta)
        assertEquals(listOf("ASISTENCIA_CAPTURAR"), titular.permisos)

        val encargo = contextos[1]
        assertEquals("ENCARGADO", encargo.tipo)
        assertEquals("2026-12-01T06:00:00.000Z", encargo.vigenteHasta)

        val hospital = contextos[2]
        assertNull(hospital.divisionId)
        assertNull(hospital.divisionNombre)
        assertNull(hospital.servicioId)
        assertNull(hospital.servicioNombre)
    }

    @Test
    fun repositorio_traduceAlModeloSinInferirFacultadesDelTipo() = runTest {
        servidor.enqueue(respuestaJson(200, ContratoIam.CONTEXTOS))

        val resultado = RemotoContextosRepository(api).contextosPropios()

        val contextos = (resultado as ResultadoContextos.Exito).contextos
        assertEquals(listOf(RolAlcance.TITULAR, RolAlcance.ENCARGADO, RolAlcance.TITULAR), contextos.map { it.rol })
        assertEquals("El tipo TITULAR no agrega permisos", listOf("CATALOGOS_ADMINISTRAR"), contextos[2].permisos)
    }

    @Test
    fun tipoDesconocido_quedaSinRol() = runTest {
        servidor.enqueue(respuestaJson(200, ContratoIam.CONTEXTOS.replaceFirst("\"tipo\":\"TITULAR\"", "\"tipo\":\"SUPLENTE\"")))

        val contextos = (RemotoContextosRepository(api).contextosPropios() as ResultadoContextos.Exito).contextos

        assertNull(contextos[0].rol)
        assertEquals(listOf("ASISTENCIA_CAPTURAR"), contextos[0].permisos)
    }

    @Test
    fun listaVacia_esExito() = runTest {
        servidor.enqueue(respuestaJson(200, "[]"))

        assertEquals(ResultadoContextos.Exito(emptyList()), RemotoContextosRepository(api).contextosPropios())
    }

    @Test
    fun contextosNoDisponibles503_esNoDisponibleParaUsarElRespaldo() = runTest {
        val cuerpo = ContratoIam.errorNest(503, "Contextos no disponibles", "Service Unavailable")
        servidor.enqueue(respuestaJson(503, cuerpo))

        assertEquals(ResultadoContextos.NoDisponible(503), RemotoContextosRepository(api).contextosPropios())
        assertEquals(CausaErrorApi.CONTEXTOS_NO_DISPONIBLES, ApiErrorParser(SigehJson).parse(503, cuerpo).causa)
        assertEquals(1, servidor.requestCount)
    }

    @Test
    fun cuerpoIlegible_esNoDisponible() = runTest {
        servidor.enqueue(respuestaJson(200, """{"no":"es una lista"}"""))

        assertEquals(ResultadoContextos.NoDisponible(null), RemotoContextosRepository(api).contextosPropios())
    }

    @Test
    fun dos401_unaSolaRenovacionYSesionVencida() = runTest {
        val noAutorizado = ContratoIam.errorNest(401, "Credenciales o sesión inválidas", "Unauthorized")
        servidor.enqueue(respuestaJson(401, noAutorizado))
        servidor.enqueue(respuestaJson(401, noAutorizado))

        assertEquals(ResultadoContextos.SesionVencida, RemotoContextosRepository(api).contextosPropios())

        servidor.takeRequest()
        assertEquals("/api/v1/auth/refresh", servidor.takeRequest().url.encodedPath)
        assertEquals("La petición original y un único refresh", 2, servidor.requestCount)
        assertTrue(cliente.sesiones.actual()!!.vencida)
    }

    @Test
    fun causaSinFiltros_seReconoce() {
        val cuerpo = ContratoIam.errorNest(400, "Contextos propios no admite filtros", "Bad Request")
        assertEquals(CausaErrorApi.CONTEXTOS_SIN_FILTROS, ApiErrorParser(SigehJson).parse(400, cuerpo).causa)
    }
}
