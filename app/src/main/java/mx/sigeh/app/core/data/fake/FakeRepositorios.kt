package mx.sigeh.app.core.data.fake

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mx.sigeh.app.core.auth.Session
import mx.sigeh.app.core.common.GeneradorIds
import mx.sigeh.app.core.common.Reloj
import mx.sigeh.app.core.data.AlcanceRepository
import mx.sigeh.app.core.data.AuthRepository
import mx.sigeh.app.core.data.CoberturaRepository
import mx.sigeh.app.core.data.ComandoSync
import mx.sigeh.app.core.data.IncidenciaRepository
import mx.sigeh.app.core.data.MovimientosRepository
import mx.sigeh.app.core.data.PaseListaRepository
import mx.sigeh.app.core.data.ProgramacionRepository
import mx.sigeh.app.core.data.RespuestaConvenio
import mx.sigeh.app.core.data.ResultadoLogin
import mx.sigeh.app.core.data.ResultadoLote
import mx.sigeh.app.core.data.ServidorRepository
import mx.sigeh.app.core.data.SyncRepository
import mx.sigeh.app.core.database.dao.CachedProgramacionDao
import mx.sigeh.app.core.database.dao.PendingCommandDao
import mx.sigeh.app.core.database.dao.SyncReceiptDao
import mx.sigeh.app.core.database.entity.CachedProgramacionEntity
import mx.sigeh.app.core.model.Agenda
import mx.sigeh.app.core.model.Alcance
import mx.sigeh.app.core.model.AsignacionOperativa
import mx.sigeh.app.core.model.ConvenioParaMi
import mx.sigeh.app.core.model.EstadoDisponibilidadEventual
import mx.sigeh.app.core.model.EstadoNecesidadCobertura
import mx.sigeh.app.core.model.EstadoPaseLista
import mx.sigeh.app.core.model.EstadoPaseSalida
import mx.sigeh.app.core.model.JornadaProgramada
import mx.sigeh.app.core.model.Movimiento
import mx.sigeh.app.core.model.NecesidadCobertura
import mx.sigeh.app.core.model.PaseLista
import mx.sigeh.app.core.model.PaseSalidaAutorizado
import mx.sigeh.app.core.model.RegistroAsistenciaServidor
import mx.sigeh.app.core.model.RespuestaBorrador
import mx.sigeh.app.core.model.TipoIncidencia
import mx.sigeh.app.core.model.Turno
import mx.sigeh.app.core.sync.ConnectivityObserver
import mx.sigeh.app.core.sync.SyncScheduler

/*
 * Implementaciones Fake de M0. Se enlazan en DataModule (único punto para cambiarlas por las reales).
 * Simulan latencia para que los estados "Enviando…" sean visibles.
 */

private const val LATENCIA_MS = 600L

/** Siembra la cola local de ejemplo (pantalla 10) la primera vez que se abre el pase. */
@Singleton
class FakeDataSeeder @Inject constructor(
    private val pendingCommandDao: PendingCommandDao,
    private val syncReceiptDao: SyncReceiptDao,
    private val syncScheduler: SyncScheduler,
) {
    private val mutex = Mutex()

    suspend fun sembrarSiVacio() = mutex.withLock {
        if (pendingCommandDao.contar() > 0) return@withLock
        DatosFicticios.comandosIniciales().forEach { pendingCommandDao.insertar(it) }
        DatosFicticios.recibosIniciales().forEach { syncReceiptDao.insertar(it) }
        // La cola sembrada simula capturas de más temprano en el turno: se intenta enviar si hay red.
        syncScheduler.programar()
    }
}

/** En M0 cualquier usuario y contraseña no vacíos entran, pero el inicio de sesión requiere red. */
class FakeAuthRepository @Inject constructor(
    private val conectividad: ConnectivityObserver,
    private val ids: GeneradorIds,
) : AuthRepository {
    override suspend fun iniciarSesion(usuario: String, contrasena: String): ResultadoLogin {
        delay(LATENCIA_MS)
        if (!conectividad.enLinea.value) return ResultadoLogin.SinConexion
        if (usuario.isBlank() || contrasena.isBlank()) return ResultadoLogin.CredencialesInvalidas
        return ResultadoLogin.Exito(
            Session(
                usuarioId = DatosFicticios.usuario.id,
                nombreCorto = DatosFicticios.usuario.nombreCorto,
                accessToken = "m0-ficticio-${ids.nuevoId()}",
            ),
        )
    }

    override suspend fun cerrarSesion() = Unit
}

class FakeServidorRepository @Inject constructor() : ServidorRepository {
    override val modoReal: Boolean = false

    override suspend fun versionContrato(): String? = null
}

class FakeAlcanceRepository @Inject constructor(
    private val reloj: Reloj,
) : AlcanceRepository {
    override suspend fun alcancesVigentes(): List<Alcance> {
        val ahora = reloj.ahoraMillis()
        return DatosFicticios.alcances(ahora).filter { it.estaVigente(ahora) }
    }

    override suspend fun turnos(servicioId: String): List<Turno> = DatosFicticios.turnos

    override suspend fun nombreServicio(servicioId: String): String? =
        listOf(DatosFicticios.medicinaInterna, DatosFicticios.cirugiaGeneral).firstOrNull { it.id == servicioId }?.nombre
}

class FakeProgramacionRepository @Inject constructor(
    private val cachedProgramacionDao: CachedProgramacionDao,
) : ProgramacionRepository {
    override fun observarAgenda(servicioId: String, turnoId: String): Flow<Agenda?> {
        if (servicioId != DatosFicticios.medicinaInterna.id || turnoId != DatosFicticios.matutino.id) {
            return flowOf(null)
        }
        return cachedProgramacionDao.observar(DatosFicticios.PROGRAMACION_PROPIA_ID)
            .map { copia -> DatosFicticios.agenda(copiaGuardada = copia?.fetchedAt) }
            .onStart {
                cachedProgramacionDao.guardar(
                    CachedProgramacionEntity(
                        programacionId = DatosFicticios.PROGRAMACION_PROPIA_ID,
                        hospitalId = DatosFicticios.hospitalA.id,
                        servicioId = servicioId,
                        startUtc = DatosFicticios.inicioTurnoMillis,
                        endUtc = DatosFicticios.finTurnoMillis,
                        version = DatosFicticios.PROGRAMACION_VERSION,
                        fetchedAt = DatosFicticios.copiaGuardadaMillis,
                        expiresAt = DatosFicticios.copiaGuardadaMillis + DIAS_30,
                    ),
                )
            }
    }

    private companion object {
        const val DIAS_30 = 30L * 24 * 60 * 60 * 1000
    }
}

@Singleton
class FakePaseListaRepository @Inject constructor(
    private val servidor: FakeServidor,
    private val seeder: FakeDataSeeder,
) : PaseListaRepository {

    private val estado = MutableStateFlow(EstadoPaseLista.BORRADOR)
    private val envioSolicitado = MutableStateFlow<Long?>(null)

    override fun observarPase(servicioId: String, turnoId: String): Flow<PaseLista?> {
        if (servicioId != DatosFicticios.medicinaInterna.id || turnoId != DatosFicticios.matutino.id) {
            return flowOf(null)
        }
        return combine(servidor.registros, estado, envioSolicitado) { registros, estadoPase, envio ->
            PaseLista(
                id = DatosFicticios.PASE_ID,
                programacionVersion = DatosFicticios.PROGRAMACION_VERSION,
                fechaCorta = DatosFicticios.FECHA_CORTA,
                estado = estadoPase,
                jornadas = DatosFicticios.jornadas,
                registros = registros,
                envioSolicitadoMillis = envio,
            )
        }.onStart { seeder.sembrarSiVacio() }
    }

    override fun observarRegistroServidor(programacionId: String): Flow<RegistroAsistenciaServidor?> =
        servidor.registros.map { it[programacionId] }

    override suspend fun jornada(programacionId: String): JornadaProgramada? =
        DatosFicticios.jornadas.firstOrNull { it.programacionId == programacionId }

    override suspend fun guardarBorrador(paseId: String) {
        estado.value = EstadoPaseLista.BORRADOR
    }

    override suspend fun solicitarEnvioAValidacion(paseId: String, solicitadoEnMillis: Long) {
        envioSolicitado.value = solicitadoEnMillis
    }
}

class FakeIncidenciaRepository @Inject constructor() : IncidenciaRepository {
    override suspend fun tiposIncidencia(hospitalId: String): List<TipoIncidencia> = DatosFicticios.tiposIncidencia
}

@Singleton
class FakeMovimientosRepository @Inject constructor() : MovimientosRepository {

    private val movimientos = MutableStateFlow(DatosFicticios.movimientosServicio)
    private val convenios = MutableStateFlow(DatosFicticios.conveniosParaMi)

    override fun observarMovimientosDelServicio(servicioId: String): Flow<List<Movimiento>> =
        if (servicioId == DatosFicticios.medicinaInterna.id) movimientos else flowOf(emptyList())

    override fun observarConveniosParaMi(): Flow<List<ConvenioParaMi>> = convenios

    override suspend fun responderConvenio(movimientoId: String, aceptar: Boolean, enLinea: Boolean): RespuestaConvenio {
        if (!enLinea) {
            val borrador = if (aceptar) RespuestaBorrador.ACEPTAR else RespuestaBorrador.RECHAZAR
            convenios.update { lista ->
                lista.map { if (it.movimientoId == movimientoId) it.copy(respuestaBorrador = borrador) else it }
            }
            return RespuestaConvenio.GUARDADA_COMO_BORRADOR
        }
        delay(LATENCIA_MS)
        convenios.update { lista -> lista.filterNot { it.movimientoId == movimientoId } }
        return RespuestaConvenio.ENVIADA
    }

    override suspend fun paseAutorizadoHoy(programacionId: String): PaseSalidaAutorizado? =
        if (programacionId == PROGRAMACION_CON_PASE) PaseSalidaAutorizado(programacionId, "11:30") else null

    override suspend fun registrarSalida(
        programacionId: String,
        horaSalida: String,
        horaRegreso: String?,
        nota: String?,
    ): Boolean {
        if (programacionId != PROGRAMACION_CON_PASE) return false
        delay(LATENCIA_MS)
        movimientos.update { lista ->
            lista.map { movimiento ->
                if (movimiento.id != MOVIMIENTO_PASE) return@map movimiento
                movimiento.copy(
                    titulo = "D. Cruz · salida $horaSalida",
                    detalle = if (horaRegreso != null) {
                        "Hoy · regreso a las $horaRegreso"
                    } else {
                        "Hoy · regreso sin registrar (no es obligatorio)"
                    },
                    estadoPase = if (horaRegreso != null) {
                        EstadoPaseSalida.REGRESO_REGISTRADO
                    } else {
                        EstadoPaseSalida.SALIDA_REGISTRADA
                    },
                )
            }
        }
        return true
    }

    private companion object {
        const val PROGRAMACION_CON_PASE = "prog-0006"
        const val MOVIMIENTO_PASE = "mov-0002"
    }
}

@Singleton
class FakeCoberturaRepository @Inject constructor() : CoberturaRepository {

    private val necesidades = MutableStateFlow(DatosFicticios.necesidades)
    private val asignaciones = MutableStateFlow(mapOf(DatosFicticios.ASIGNACION_ID to DatosFicticios.asignacionSofia))

    override fun observarNecesidades(servicioId: String): Flow<List<NecesidadCobertura>> =
        if (servicioId == DatosFicticios.medicinaInterna.id) necesidades else flowOf(emptyList())

    override fun observarAsignacion(asignacionId: String): Flow<AsignacionOperativa?> =
        asignaciones.map { it[asignacionId] }

    override suspend fun confirmarIncorporacion(asignacionId: String, inicioEfectivo: String): Boolean {
        val asignacion = asignaciones.value[asignacionId] ?: return false
        delay(LATENCIA_MS)
        asignaciones.update {
            it + (asignacionId to asignacion.copy(
                estadoEventual = EstadoDisponibilidadEventual.INCORPORADA,
                incorporadaA = inicioEfectivo,
            ))
        }
        // Simulación: en el sistema real la cobertura efectiva la aplica Coordinación (CU-COB-12).
        necesidades.update { lista ->
            lista.map {
                if (it.id != asignacion.necesidad.id) return@map it
                it.copy(
                    estado = EstadoNecesidadCobertura.CUBIERTA,
                    detalle = "${asignacion.persona.nombre} se incorporó a las $inicioEfectivo",
                    asignacionPendienteId = null,
                )
            }
        }
        return true
    }

    override suspend fun rechazarIncorporacion(asignacionId: String, motivo: String): Boolean {
        val asignacion = asignaciones.value[asignacionId] ?: return false
        delay(LATENCIA_MS)
        asignaciones.update {
            it + (asignacionId to asignacion.copy(estadoEventual = EstadoDisponibilidadEventual.LIBERADA))
        }
        necesidades.update { lista ->
            lista.map {
                if (it.id != asignacion.necesidad.id) return@map it
                it.copy(detalle = "Incorporación rechazada · Coordinación busca personal eventual", asignacionPendienteId = null)
            }
        }
        return true
    }
}

class FakeSyncRepository @Inject constructor(
    private val servidor: FakeServidor,
) : SyncRepository {
    override suspend fun enviarLote(comandos: List<ComandoSync>): ResultadoLote {
        delay(LATENCIA_MS)
        return servidor.recibirLote(comandos)
    }
}
