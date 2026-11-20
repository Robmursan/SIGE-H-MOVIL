package mx.sigeh.app.features.paselista

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import mx.sigeh.app.core.auth.ScopeCache
import mx.sigeh.app.core.auth.Session
import mx.sigeh.app.core.common.HoraHospital
import mx.sigeh.app.core.data.MotivoRechazo
import mx.sigeh.app.core.data.ResultadoCaptura
import mx.sigeh.app.core.data.ResultadoConsulta
import mx.sigeh.app.core.data.fake.DatosFicticios
import mx.sigeh.app.core.model.EstadoCaptura
import mx.sigeh.app.testutil.ConectividadFalsa
import mx.sigeh.app.testutil.IdsSecuenciales
import mx.sigeh.app.testutil.MainDispatcherRule
import mx.sigeh.app.testutil.PaseListaGuionado
import mx.sigeh.app.testutil.RelojFijo
import mx.sigeh.app.testutil.SessionStoreEnMemoria
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Criterio de aceptación de M2: «cada respuesta de error de la sección 6.6 tiene su pantalla o
 * mensaje», y la `Idempotency-Key` solo se descarta con una respuesta terminal (§5.5).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PaseListaErroresTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val capturable = "p-02"

    private class Escenario(val repo: PaseListaGuionado, val sesion: SessionStoreEnMemoria, val savedState: SavedStateHandle, val vm: PaseListaViewModel)

    private fun TestScope.escenario(): Escenario {
        val repo = PaseListaGuionado()
        val sesion = SessionStoreEnMemoria(Session(DatosFicticios.usuario.id, "M. Hernández", "token-de-prueba"))
        val savedState = SavedStateHandle()
        val scope = ScopeCache().apply { seleccionarServicio(PaseListaGuionado.SERVICIO) }
        val vm = PaseListaViewModel(
            savedState, scope, repo, sesion, ConectividadFalsa(enLinea = true),
            RelojFijo(HoraHospital.instante("2026-11-20T13:30:00Z")!!), IdsSecuenciales(),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return Escenario(repo, sesion, savedState, vm)
    }

    /** Qué debe pasar en la pantalla con cada resultado del `PUT`. */
    private data class Esperado(
        val dialogo: DialogoCaptura? = null,
        val bloqueo: BloqueoPase? = null,
        val conservaClave: Boolean,
        val recarga: Boolean,
    )

    @Test
    fun cadaResultadoDeCapturaTieneSuMensaje() = runTest {
        val casos: List<Pair<ResultadoCaptura, Esperado>> = listOf(
            ResultadoCaptura.Rechazada(MotivoRechazo.YA_REGISTRADA) to
                Esperado(DialogoCaptura.YaRegistrada(capturable), conservaClave = false, recarga = true),
            ResultadoCaptura.Rechazada(MotivoRechazo.FUERA_VENTANA) to
                Esperado(DialogoCaptura.FueraDeHorario, conservaClave = false, recarga = true),
            ResultadoCaptura.Rechazada(MotivoRechazo.PROGRAMACION_NO_CAPTURABLE) to
                Esperado(DialogoCaptura.NoAdmiteCaptura("La programación de esta jornada ya no admite captura."), conservaClave = false, recarga = true),
            ResultadoCaptura.Rechazada(MotivoRechazo.CONTRATO_NO_VIGENTE) to
                Esperado(DialogoCaptura.NoAdmiteCaptura("El contrato de la persona no está vigente."), conservaClave = false, recarga = true),
            ResultadoCaptura.Rechazada(MotivoRechazo.CIERRE_EXISTENTE) to
                Esperado(DialogoCaptura.NoAdmiteCaptura("El balance del periodo ya está cerrado."), conservaClave = false, recarga = true),
            ResultadoCaptura.Rechazada(MotivoRechazo.NO_ADMITE_CAPTURA) to
                Esperado(DialogoCaptura.NoAdmiteCaptura("El servidor no admite captura para esta jornada."), conservaClave = false, recarga = true),
            ResultadoCaptura.Rechazada(MotivoRechazo.CONFLICTO_IDEMPOTENCIA) to
                Esperado(DialogoCaptura.ErrorInterno, conservaClave = false, recarga = true),
            ResultadoCaptura.Rechazada(MotivoRechazo.SOLICITUD_INVALIDA) to
                Esperado(DialogoCaptura.ErrorInterno, conservaClave = false, recarga = true),
            ResultadoCaptura.NoEncontrada to Esperado(DialogoCaptura.NoDisponible, conservaClave = false, recarga = true),
            ResultadoCaptura.SinPermiso to Esperado(bloqueo = BloqueoPase.SIN_PERMISO, conservaClave = false, recarga = false),
            ResultadoCaptura.Incierta to
                Esperado(DialogoCaptura.Incierta(capturable, EstadoCaptura.PRESENTE), conservaClave = true, recarga = false),
            ResultadoCaptura.NoHabilitada to Esperado(DialogoCaptura.NoHabilitada, conservaClave = true, recarga = false),
            ResultadoCaptura.SesionTerminada to Esperado(bloqueo = BloqueoPase.SESION_TERMINADA, conservaClave = true, recarga = false),
        )

        casos.forEach { (resultado, esperado) ->
            val e = escenario()
            e.repo.resultados += resultado

            e.vm.onAbrirCaptura(capturable, EstadoCaptura.PRESENTE)
            e.vm.onConfirmar(EstadoCaptura.PRESENTE)

            val estado = e.vm.uiState.value
            assertEquals("$resultado: diálogo", esperado.dialogo, estado.dialogo)
            assertEquals("$resultado: bloqueo", esperado.bloqueo, estado.bloqueo)
            assertNull("$resultado: la hoja se cierra", estado.hoja)
            val clave = e.repo.capturas.single().clave
            val guardadas = e.savedState.get<String>(PaseListaViewModel.CLAVE_INTENCIONES).orEmpty()
            assertEquals("$resultado: ¿conserva la clave?", esperado.conservaClave, guardadas.contains(clave))
            assertEquals("$resultado: ¿recarga la lista?", if (esperado.recarga) 2 else 1, e.repo.consultas.size)
            if (resultado == ResultadoCaptura.SesionTerminada) assertTrue(e.sesion.actual()!!.vencida)
        }
    }

    @Test
    fun cadaResultadoDeConsultaTieneSuPantalla() = runTest {
        val casos = listOf(
            ResultadoConsulta.SinPermiso to BloqueoPase.SIN_PERMISO,
            ResultadoConsulta.ContextoNoDisponible to BloqueoPase.CONTEXTO_NO_DISPONIBLE,
            ResultadoConsulta.NoHabilitada to BloqueoPase.NO_HABILITADO,
            ResultadoConsulta.SesionTerminada to BloqueoPase.SESION_TERMINADA,
        )
        casos.forEach { (resultado, bloqueo) ->
            val e = escenario()
            e.repo.resultadoConsulta = resultado
            e.vm.onActualizar()

            assertEquals("$resultado", bloqueo, e.vm.uiState.value.bloqueo)
            assertFalse(e.vm.uiState.value.cargando)
        }
    }

    @Test
    fun consultaFallida_ofreceReintentarSinBloquear() = runTest {
        val e = escenario()
        e.repo.resultadoConsulta = ResultadoConsulta.Fallida

        e.vm.onActualizar()

        assertTrue(e.vm.uiState.value.errorCarga)
        assertNull(e.vm.uiState.value.bloqueo)
        e.repo.resultadoConsulta = null
        e.vm.onActualizar()
        assertFalse(e.vm.uiState.value.errorCarga)
        assertEquals(7, e.vm.uiState.value.filas.size)
    }

    @Test
    fun diaSinJornadas_esListaVacia() = runTest {
        val e = escenario()
        e.repo.jornadas = emptyList()

        e.vm.onActualizar()

        assertTrue(e.vm.uiState.value.vacio)
        assertEquals(0, e.vm.uiState.value.capturables)
    }
}
