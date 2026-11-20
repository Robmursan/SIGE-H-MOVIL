package mx.sigeh.app.features.cuenta

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import mx.sigeh.app.core.auth.ScopeCache
import mx.sigeh.app.core.auth.SesionManager
import mx.sigeh.app.core.auth.Session
import mx.sigeh.app.core.common.Interruptores
import mx.sigeh.app.core.data.AuthRepository
import mx.sigeh.app.core.data.ResultadoLogin
import mx.sigeh.app.core.data.ResultadoRevocacion
import mx.sigeh.app.core.data.ResultadoSesiones
import mx.sigeh.app.core.data.ServidorRepository
import mx.sigeh.app.core.data.SesionIam
import mx.sigeh.app.core.data.SesionesRepository
import mx.sigeh.app.core.database.PurgaLocal
import mx.sigeh.app.testutil.MainDispatcherRule
import mx.sigeh.app.testutil.PaseListaGuionado
import mx.sigeh.app.testutil.PendingCommandDaoFalso
import mx.sigeh.app.testutil.SchedulerContador
import mx.sigeh.app.testutil.SessionStoreEnMemoria
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CuentaViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private class Sesiones : SesionesRepository {
        var lista = listOf(
            SesionIam("actual", "2026-11-20T13:00:00.000Z", "2026-11-20T21:00:00.000Z", "2026-11-20T13:30:00.000Z", null, actual = true),
            SesionIam("tableta", "2026-11-20T12:40:00.000Z", "2026-11-20T20:40:00.000Z", "2026-11-20T13:10:00.000Z", null, actual = false),
        )
        var resultadoLista: ResultadoSesiones? = null
        var resultadoRevocar: ResultadoRevocacion? = null
        val revocadas = mutableListOf<String>()

        override suspend fun sesiones(): ResultadoSesiones = resultadoLista ?: ResultadoSesiones.Exito(lista)

        override suspend fun revocar(sessionId: String): ResultadoRevocacion {
            revocadas += sessionId
            resultadoRevocar?.let { return it }
            lista = lista.map { if (it.sessionId == sessionId) it.copy(revocadaEn = "2026-11-20T13:40:00.000Z") else it }
            return ResultadoRevocacion.Revocada(eraLaActual = sessionId == "actual")
        }
    }

    private val sesionStore = SessionStoreEnMemoria(Session("u", "cuenta_ficticia", "t", identificador = "cuenta_ficticia"))
    private val repo = Sesiones()

    private fun crear(): CuentaViewModel {
        val scope = ScopeCache().apply { seleccionarServicio(PaseListaGuionado.SERVICIO) }
        val auth = object : AuthRepository {
            override suspend fun iniciarSesion(usuario: String, contrasena: String) = ResultadoLogin.SinConexion
            override suspend fun cerrarSesion() = Unit
        }
        val manager = SesionManager(
            auth, sesionStore, scope,
            object : PurgaLocal {
                override suspend fun purgarTodo() = Unit
            },
            PendingCommandDaoFalso(), SchedulerContador(), PaseListaGuionado(), Interruptores(false),
        )
        val servidor = object : ServidorRepository {
            override val modoReal = true
            override suspend fun versionContrato() = "1.0.0"
        }
        return CuentaViewModel(sesionStore, scope, repo, manager, servidor, PaseListaGuionado(), Interruptores(false))
    }

    @Test
    fun muestraIdentificadorServicioSesionesYVersiones() = runTest {
        val estado = crear().uiState.value

        assertEquals("cuenta_ficticia", estado.identificador)
        assertEquals("Medicina Interna", estado.servicio)
        assertEquals("App 0.2.0-m2 · contrato API 1.0.0", estado.pie)
        assertEquals(
            "Iniciada 20 nov 07:00 · Expira 20 nov 15:00 · inactividad 20 nov 07:30",
            estado.sesiones.single { it.actual }.detalle,
        )
    }

    @Test
    fun cerrarOtraSesion_laMarcaRevocadaSinCerrarLaPropia() = runTest {
        val vm = crear()
        val eventos = mutableListOf<CuentaEvento>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.eventos.toList(eventos) }

        vm.onRevocar("tableta")

        assertEquals(listOf("tableta"), repo.revocadas)
        assertTrue(vm.uiState.value.sesiones.single { it.sessionId == "tableta" }.revocada)
        assertTrue("No cierra la sesión de este dispositivo", eventos.isEmpty())
        assertEquals("cuenta_ficticia", sesionStore.actual()!!.identificador)
    }

    @Test
    fun errores_seMuestranConUnMensaje() = runTest {
        repo.resultadoRevocar = ResultadoRevocacion.NoEncontrada
        val vm = crear()
        vm.onRevocar("tableta")
        assertEquals("Esa sesión ya no está disponible.", vm.uiState.value.errorSesiones)

        repo.resultadoRevocar = ResultadoRevocacion.Fallida
        vm.onRevocar("tableta")
        assertEquals("No se pudo cerrar la sesión. Intenta de nuevo.", vm.uiState.value.errorSesiones)

        repo.resultadoLista = ResultadoSesiones.Fallida
        vm.cargarSesiones()
        assertEquals("No se pudieron cargar las sesiones.", vm.uiState.value.errorSesiones)

        repo.resultadoLista = ResultadoSesiones.SesionTerminada
        vm.cargarSesiones()
        assertTrue(sesionStore.actual()!!.vencida)
    }
}
