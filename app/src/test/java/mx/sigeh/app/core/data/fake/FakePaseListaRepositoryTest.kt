package mx.sigeh.app.core.data.fake

import java.math.BigInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import mx.sigeh.app.core.auth.Session
import mx.sigeh.app.core.data.MotivoRechazo
import mx.sigeh.app.core.data.ResultadoCaptura
import mx.sigeh.app.core.data.ResultadoConsulta
import mx.sigeh.app.core.model.EstadoAsistencia
import mx.sigeh.app.core.model.EstadoCaptura
import mx.sigeh.app.core.model.EstadoProgramacion
import mx.sigeh.app.core.model.JornadaPase
import mx.sigeh.app.core.model.MotivoNoCapturable
import mx.sigeh.app.core.model.ValidacionAdministrativa
import mx.sigeh.app.testutil.RelojFijo
import mx.sigeh.app.testutil.SessionStoreEnMemoria
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** El modo Fake se comporta como el backend (prompt M2 §10): es el de la demostración. */
@OptIn(ExperimentalCoroutinesApi::class)
class FakePaseListaRepositoryTest {

    private val fecha = "2026-11-20"
    private val medicina = DatosFicticios.medicinaInterna.id
    private val cirugia = DatosFicticios.cirugiaGeneral.id
    private val sesion = SessionStoreEnMemoria(
        Session(
            usuarioId = DatosFicticios.usuario.id,
            nombreCorto = DatosFicticios.usuario.nombreCorto,
            accessToken = "m0-ficticio",
            asignaciones = DatosFicticios.asignaciones,
        ),
    )
    private val repositorio = FakePaseListaRepository(sesion, RelojFijo(1_763_643_900_000L))

    private suspend fun jornadas(servicioId: String = medicina, horarioId: String? = null): List<JornadaPase> =
        (repositorio.consultar(DatosFicticios.hospitalA.id, servicioId, fecha, horarioId) as ResultadoConsulta.Exito).pagina.jornadas

    private suspend fun porCapturar(): List<JornadaPase> = jornadas().filter { it.capturable }

    @Test
    fun consultar_sieteJornadasConUnCasoDeCadaTipo() = runTest {
        val resultado = repositorio.consultar(DatosFicticios.hospitalA.id, medicina, fecha)

        val pagina = (resultado as ResultadoConsulta.Exito).pagina
        assertEquals(DatosFicticios.ZONA_HOSPITAL, pagina.zonaHoraria)
        assertEquals(fecha, pagina.fecha)
        val lista = pagina.jornadas
        assertEquals(7, lista.size)
        assertEquals("Dos por capturar", 2, lista.count { it.capturable })
        assertTrue("Nada está marcado por defecto", lista.filter { it.capturable }.all { it.estadoOperativo == EstadoAsistencia.PENDIENTE })

        val propias = lista.filter { it.ultimaCaptura?.actorId == DatosFicticios.usuario.id }
        assertEquals(setOf(EstadoAsistencia.PRESENTE, EstadoAsistencia.PARCIAL), propias.map { it.estadoOperativo }.toSet())
        val ajena = lista.single { it.capturada && it.ultimaCaptura!!.actorId != DatosFicticios.usuario.id }
        assertEquals(EstadoAsistencia.AUSENTE, ajena.estadoOperativo)
        assertEquals(ValidacionAdministrativa.VALIDADA, lista.single { it.estadoOperativo == EstadoAsistencia.PARCIAL }.validacion)

        val cancelada = lista.single { it.estadoProgramacion == EstadoProgramacion.CANCELADA }
        assertEquals(MotivoNoCapturable.PROGRAMACION_NO_CAPTURABLE, cancelada.motivoNoCapturable)
        val vespertina = lista.single { it.motivoNoCapturable == MotivoNoCapturable.FUERA_VENTANA }
        assertEquals("TV", vespertina.turnoCodigo)
        assertEquals("2026-11-20T14:30:00.000-06:00", vespertina.inicioProgramado)
        assertTrue(lista.filter { it.capturada }.all { it.motivoNoCapturable == MotivoNoCapturable.YA_REGISTRADA })
    }

    @Test
    fun consultar_tieneLatenciaDe400a800ms() = runTest {
        repositorio.consultar(DatosFicticios.hospitalA.id, medicina, fecha)

        assertTrue("Latencia ${testScheduler.currentTime} ms", testScheduler.currentTime in 400L..800L)
    }

    @Test
    fun consultar_filtraPorHorario() = runTest {
        val vespertinas = jornadas(horarioId = DatosFicticios.HORARIO_TV_ID)

        assertEquals(listOf("Héctor Ruiz"), vespertinas.map { it.nombre })
    }

    @Test
    fun capturar_confirmaYLaJornadaQuedaRegistradaPorLaPersonaDeLaSesion() = runTest {
        val jornada = porCapturar().first()

        val resultado = repositorio.capturar(jornada.programacionId, EstadoCaptura.PARCIAL, "clave-1")

        val acuse = (resultado as ResultadoCaptura.Confirmada).acuse
        assertEquals(EstadoCaptura.PARCIAL, acuse.estado)
        assertEquals(ValidacionAdministrativa.PENDIENTE, acuse.validacion)
        assertEquals(DatosFicticios.usuario.id, acuse.actorId)
        assertEquals("1", acuse.revision)
        assertTrue("eventoId mayor que 2^53, como texto", BigInteger(acuse.eventoId) > BigInteger.TWO.pow(53))
        assertTrue(acuse.registradoEn.endsWith("-06:00"))
        assertEquals(acuse, repositorio.acuse(jornada.programacionId))

        val despues = jornadas().single { it.programacionId == jornada.programacionId }
        assertTrue(despues.capturada)
        assertFalse(despues.capturable)
        assertEquals(EstadoAsistencia.PARCIAL, despues.estadoOperativo)
        assertEquals(acuse.eventoId, despues.ultimaCaptura!!.eventoId)
        assertEquals(MotivoNoCapturable.YA_REGISTRADA, despues.motivoNoCapturable)
    }

    @Test
    fun mismaClaveYMismoEstado_devuelveElMismoAcuse() = runTest {
        val id = porCapturar().first().programacionId

        val primero = repositorio.capturar(id, EstadoCaptura.PRESENTE, "clave-1")
        val reintento = repositorio.capturar(id, EstadoCaptura.PRESENTE, "clave-1")

        assertEquals(primero, reintento)
    }

    @Test
    fun claveNuevaSobreUnaJornadaCapturada_esYaRegistrada() = runTest {
        val id = porCapturar().first().programacionId
        repositorio.capturar(id, EstadoCaptura.PRESENTE, "clave-1")

        assertEquals(
            ResultadoCaptura.Rechazada(MotivoRechazo.YA_REGISTRADA),
            repositorio.capturar(id, EstadoCaptura.PRESENTE, "clave-2"),
        )
    }

    @Test
    fun mismaClaveConOtroEstado_esConflictoDeIdempotencia() = runTest {
        val (una, otra) = porCapturar()
        repositorio.capturar(una.programacionId, EstadoCaptura.PRESENTE, "clave-1")

        val conflicto = ResultadoCaptura.Rechazada(MotivoRechazo.CONFLICTO_IDEMPOTENCIA)
        assertEquals(conflicto, repositorio.capturar(una.programacionId, EstadoCaptura.AUSENTE, "clave-1"))
        assertEquals("Ni en otra jornada", conflicto, repositorio.capturar(otra.programacionId, EstadoCaptura.PRESENTE, "clave-1"))
        assertTrue("La otra jornada sigue sin capturar", jornadas().single { it.programacionId == otra.programacionId }.capturable)
    }

    @Test
    fun jornadasNoCapturables_seRechazanConSuMotivo() = runTest {
        val lista = jornadas()
        val vespertina = lista.single { it.motivoNoCapturable == MotivoNoCapturable.FUERA_VENTANA }
        val cancelada = lista.single { it.estadoProgramacion == EstadoProgramacion.CANCELADA }

        assertEquals(
            ResultadoCaptura.Rechazada(MotivoRechazo.FUERA_VENTANA),
            repositorio.capturar(vespertina.programacionId, EstadoCaptura.PRESENTE, "c-1"),
        )
        assertEquals(
            ResultadoCaptura.Rechazada(MotivoRechazo.PROGRAMACION_NO_CAPTURABLE),
            repositorio.capturar(cancelada.programacionId, EstadoCaptura.PRESENTE, "c-2"),
        )
        assertEquals(ResultadoCaptura.NoEncontrada, repositorio.capturar("no-existe", EstadoCaptura.PRESENTE, "c-3"))
    }

    @Test
    fun servicioSoloConsulta_noOfreceCapturaYElPutEsSinPermiso() = runTest {
        val lista = jornadas(servicioId = cirugia)

        assertTrue(lista.none { it.capturable })
        val antesCapturables = lista.filter { it.motivoNoCapturable == MotivoNoCapturable.SIN_FACULTAD_CAPTURA }
        assertEquals(2, antesCapturables.size)
        assertEquals(
            "Los otros motivos tienen prioridad",
            1,
            lista.count { it.motivoNoCapturable == MotivoNoCapturable.FUERA_VENTANA },
        )
        assertEquals(ResultadoCaptura.SinPermiso, repositorio.capturar(antesCapturables.first().programacionId, EstadoCaptura.PRESENTE, "c-1"))
    }

    @Test
    fun servicioSinAsignacion_esSinPermiso() = runTest {
        assertEquals(ResultadoConsulta.SinPermiso, repositorio.consultar(DatosFicticios.hospitalA.id, "svc-ajeno", fecha))
    }

    @Test
    fun cadaServicioYFechaTieneSusPropiasJornadas() = runTest {
        val medicinaHoy = jornadas().map { it.programacionId }
        val cirugiaHoy = jornadas(servicioId = cirugia).map { it.programacionId }

        assertTrue(medicinaHoy.intersect(cirugiaHoy.toSet()).isEmpty())
        val otroDia = (repositorio.consultar(DatosFicticios.hospitalA.id, medicina, "2026-11-21") as ResultadoConsulta.Exito)
        assertNotEquals(medicinaHoy, otroDia.pagina.jornadas.map { it.programacionId })
    }

    @Test
    fun limpiar_soloPurgaLaCacheDeLaApp() = runTest {
        val id = porCapturar().first().programacionId
        repositorio.capturar(id, EstadoCaptura.PRESENTE, "clave-1")

        repositorio.limpiar()

        assertEquals(null, repositorio.jornada(id))
        assertEquals(null, repositorio.acuse(id))
        assertTrue("El «servidor» conserva el registro", jornadas().single { it.programacionId == id }.capturada)
    }
}
