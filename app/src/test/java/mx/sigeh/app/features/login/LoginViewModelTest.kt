package mx.sigeh.app.features.login

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mx.sigeh.app.core.auth.ScopeCache
import mx.sigeh.app.core.auth.SesionManager
import mx.sigeh.app.core.common.Interruptores
import mx.sigeh.app.core.data.AuthRepository
import mx.sigeh.app.core.data.ResultadoLogin
import mx.sigeh.app.core.data.ServidorRepository
import mx.sigeh.app.core.database.PurgaLocal
import mx.sigeh.app.testutil.ConectividadFalsa
import mx.sigeh.app.testutil.MainDispatcherRule
import mx.sigeh.app.testutil.PaseListaGuionado
import mx.sigeh.app.testutil.PendingCommandDaoFalso
import mx.sigeh.app.testutil.SchedulerContador
import mx.sigeh.app.testutil.SessionStoreEnMemoria
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private class Servidor(private val version: String?) : ServidorRepository {
        override val modoReal = true
        override suspend fun versionContrato() = version
    }

    private fun crear(resultado: ResultadoLogin, servidor: ServidorRepository = Servidor("1.0.0")): LoginViewModel {
        val auth = object : AuthRepository {
            override suspend fun iniciarSesion(usuario: String, contrasena: String) = resultado
            override suspend fun cerrarSesion() = Unit
        }
        val manager = SesionManager(
            auth, SessionStoreEnMemoria(null), ScopeCache(),
            object : PurgaLocal {
                override suspend fun purgarTodo() = Unit
            },
            PendingCommandDaoFalso(), SchedulerContador(), PaseListaGuionado(), Interruptores(false),
        )
        return LoginViewModel(manager, servidor, ConectividadFalsa(enLinea = true))
    }

    private fun LoginViewModel.llenar() {
        onIdentificadorChange("cuenta_ficticia")
        onContrasenaChange("x")
    }

    @Test
    fun servidorCompatible_muestraLaVersionDelContrato() = runTest {
        val vm = crear(ResultadoLogin.CredencialesInvalidas)
        advanceUntilIdle()

        assertEquals(EstadoServidor.EnLinea("1.0.0"), vm.uiState.value.servidor)
    }

    @Test
    fun versionIncompatible_bloqueaElInicioDeSesion() = runTest {
        val vm = crear(ResultadoLogin.CredencialesInvalidas, Servidor("2.0.0"))
        advanceUntilIdle()
        vm.llenar()

        assertEquals(EstadoServidor.Incompatible("2.0.0"), vm.uiState.value.servidor)
        assertFalse(vm.uiState.value.puedeEnviar)
    }

    @Test
    fun limiteDeIntentos_desactivaElBotonConCuentaRegresiva() = runTest {
        val vm = crear(ResultadoLogin.LimiteIntentos(esperaSegundos = 3))
        advanceUntilIdle()
        vm.llenar()

        vm.iniciarSesion()
        runCurrent()
        assertEquals(3L, vm.uiState.value.esperaSegundos)
        assertFalse(vm.uiState.value.puedeEnviar)
        assertEquals("Demasiados intentos. Podrás volver a intentar en 0:03.", vm.uiState.value.error)

        advanceTimeBy(1_500)
        assertEquals(2L, vm.uiState.value.esperaSegundos)
        advanceUntilIdle()
        assertEquals(0L, vm.uiState.value.esperaSegundos)
        assertTrue(vm.uiState.value.puedeEnviar)
    }

    @Test
    fun servicioNoDisponible_yIdentificadorDeMaximo100() = runTest {
        val vm = crear(ResultadoLogin.ServicioNoDisponible)
        advanceUntilIdle()
        vm.onIdentificadorChange("a".repeat(150))
        vm.onContrasenaChange("x")

        assertEquals(LONGITUD_MAXIMA_IDENTIFICADOR, vm.uiState.value.identificador.length)
        vm.iniciarSesion()
        advanceUntilIdle()
        assertEquals("El inicio de sesión no está disponible por ahora.", vm.uiState.value.error)
    }
}
