package mx.sigeh.app.core.auth

import javax.inject.Inject
import javax.inject.Singleton
import mx.sigeh.app.core.common.Interruptores
import mx.sigeh.app.core.data.AuthRepository
import mx.sigeh.app.core.data.PaseListaRepository
import mx.sigeh.app.core.data.ResultadoLogin
import mx.sigeh.app.core.database.PurgaLocal
import mx.sigeh.app.core.database.dao.PendingCommandDao
import mx.sigeh.app.core.sync.SyncScheduler

/**
 * Orquesta inicio y cierre de sesión (FC-01).
 * - Cerrar sesión purga Room, la caché del pase en línea, la caché del ámbito y la sesión cifrada.
 * - Si entra otra persona en el mismo teléfono, se purga la cola de la anterior.
 * - La cola offline de M0 solo se programa con `FUNCIONES_FUTURAS`: la captura de M2 es en línea
 *   y nunca pasa por ella.
 */
@Singleton
class SesionManager @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionStore: SessionStore,
    private val scopeCache: ScopeCache,
    private val purgaLocal: PurgaLocal,
    private val pendingCommandDao: PendingCommandDao,
    private val syncScheduler: SyncScheduler,
    private val paseListaRepository: PaseListaRepository,
    private val interruptores: Interruptores,
) {

    suspend fun iniciarSesion(usuario: String, contrasena: String): ResultadoLogin {
        val resultado = authRepository.iniciarSesion(usuario, contrasena)
        if (resultado is ResultadoLogin.Exito) {
            if (pendingCommandDao.contarDeOtroActor(resultado.sesion.usuarioId) > 0) {
                purgaLocal.purgarTodo()
            }
            paseListaRepository.limpiar()
            sessionStore.guardar(resultado.sesion)
            if (interruptores.funcionesFuturas) syncScheduler.programar()
        }
        return resultado
    }

    suspend fun cerrarSesion() {
        syncScheduler.cancelar()
        authRepository.cerrarSesion()
        purgaLocal.purgarTodo()
        paseListaRepository.limpiar()
        scopeCache.limpiar()
        sessionStore.limpiar()
    }

    /** El alcance activo venció (solo en Fake): se purga lo local y se vuelve al selector. */
    suspend fun purgarPorAlcanceVencido() {
        syncScheduler.cancelar()
        purgaLocal.purgarTodo()
        paseListaRepository.limpiar()
        scopeCache.limpiar()
    }
}
