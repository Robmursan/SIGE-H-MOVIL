package mx.sigeh.app.features.detallejornada

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import mx.sigeh.app.core.auth.ScopeCache
import mx.sigeh.app.core.auth.Session
import mx.sigeh.app.core.data.fake.DatosFicticios
import mx.sigeh.app.core.model.EstadoAsistencia
import mx.sigeh.app.testutil.MainDispatcherRule
import mx.sigeh.app.testutil.PaseListaGuionado
import mx.sigeh.app.testutil.SessionStoreEnMemoria
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** El detalle se arma solo con la caché del pase y el acuse: no llama a ningún endpoint. */
@OptIn(ExperimentalCoroutinesApi::class)
class DetalleJornadaViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repo = PaseListaGuionado()
    private val sesion = SessionStoreEnMemoria(Session(DatosFicticios.usuario.id, "M. Hernández", "t"))
    private val scope = ScopeCache().apply { seleccionarServicio(PaseListaGuionado.SERVICIO) }

    private fun detalle(programacionId: String) =
        DetalleJornadaViewModel(SavedStateHandle(mapOf(DetalleJornadaViewModel.ARG_PROGRAMACION_ID to programacionId)), repo, scope, sesion)
            .uiState.value

    private fun List<DatoDetalle>.valor(etiqueta: String) = single { it.etiqueta == etiqueta }.valor

    @Test
    fun sinDatosEnMemoria_loDice() {
        assertFalse(detalle("p-02").encontrada)
    }

    @Test
    fun capturadaPorOtraPersona_muestraOtroUsuarioYLaHoraDelServidor() = runTest {
        repo.consultar(DatosFicticios.hospitalA.id, DatosFicticios.medicinaInterna.id, "2026-11-20")
        val consultasAntes = repo.consultas.size

        val estado = detalle("p-04")

        assertEquals("Ricardo Ibarra", estado.nombre)
        assertEquals("Medicina Interna", estado.servicio)
        assertEquals(EstadoAsistencia.AUSENTE, estado.estadoOperativo)
        assertEquals("TM", estado.programacion.valor("Turno"))
        assertEquals("07:00", estado.programacion.valor("Inicio"))
        assertEquals("14:30", estado.programacion.valor("Fin"))
        assertEquals("Programada", estado.programacion.valor("Estado de la programación"))
        assertEquals("Pendiente", estado.asistencia.valor("Validación administrativa"))
        assertEquals("1", estado.asistencia.valor("Revisión"))
        assertEquals("Otro usuario", estado.acuse!!.valor("Capturó"))
        assertEquals("07:10:45 (hora del servidor)", estado.acuse.valor("Registrado"))
        assertEquals("9007199254740994", estado.acuse.valor("Evento"))
        assertEquals("No vuelve a consultar", consultasAntes, repo.consultas.size)
    }

    @Test
    fun jornadaSinCaptura_noTieneAcuse() = runTest {
        repo.consultar(DatosFicticios.hospitalA.id, DatosFicticios.medicinaInterna.id, "2026-11-20")

        val estado = detalle("p-02")

        assertNull(estado.acuse)
        assertEquals("Por capturar", estado.asistencia.valor("Estado operativo"))
        assertEquals("Sin captura", estado.asistencia.valor("Validación administrativa"))
    }
}
