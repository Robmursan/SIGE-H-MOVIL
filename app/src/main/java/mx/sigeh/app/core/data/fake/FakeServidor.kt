package mx.sigeh.app.core.data.fake

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import mx.sigeh.app.core.common.Reloj
import mx.sigeh.app.core.data.ComandoSync
import mx.sigeh.app.core.data.ResultadoElemento
import mx.sigeh.app.core.data.ResultadoLote
import mx.sigeh.app.core.model.AsistenciaPayload
import mx.sigeh.app.core.model.CommandStatus
import mx.sigeh.app.core.model.CommandType
import mx.sigeh.app.core.model.RegistroAsistenciaServidor
import mx.sigeh.app.core.model.SigehJson

/**
 * Backend simulado en memoria (solo M0). Imita lo que la guía §12.4 exige al servidor real:
 * - Inbox de idempotencia: el mismo `clientEventId` devuelve la misma respuesta (YA_APLICADO).
 * - Un registro validado por Supervisión nunca se sobrescribe: responde CONFLICTO.
 * - Un encargo vencido produce DENEGADO por elemento.
 * La idempotencia real depende de V2-INBOX en el backend (ver docs/mobile/contrato-pendiente.md).
 */
@Singleton
class FakeServidor @Inject constructor(
    private val reloj: Reloj,
) {
    private val _registros = MutableStateFlow(DatosFicticios.registrosServidorIniciales())
    val registros: StateFlow<Map<String, RegistroAsistenciaServidor>> = _registros.asStateFlow()

    private val inbox = DatosFicticios.inboxInicial().associateBy { it.clientEventId }.toMutableMap()
    private val mutacionesPorJornada = mutableMapOf<String, Int>()
    private var siguienteEvento = 100

    /** Solo pruebas: simula que el servidor rechaza la sesión (401). */
    @Volatile
    var simularNoAutorizado: Boolean = false

    var lotesRecibidos: Int = 0
        private set

    /** Cada `Idempotency-Key` recibida, en orden (incluye reenvíos). */
    val clavesRecibidas: MutableList<String> = mutableListOf()

    @Synchronized
    fun recibirLote(comandos: List<ComandoSync>): ResultadoLote {
        lotesRecibidos++
        if (simularNoAutorizado) return ResultadoLote.NoAutorizado(401)
        clavesRecibidas += comandos.map { it.clientEventId }
        return ResultadoLote.Respuestas(comandos.map(::aplicar))
    }

    /** Cuántas veces cambió el registro de una jornada (para comprobar que un reenvío no duplica). */
    @Synchronized
    fun mutaciones(programacionId: String): Int = mutacionesPorJornada[programacionId] ?: 0

    private fun aplicar(comando: ComandoSync): ResultadoElemento {
        inbox[comando.clientEventId]?.let { previa ->
            return if (previa.estado == CommandStatus.APLICADO) {
                previa.copy(estado = CommandStatus.YA_APLICADO, httpCode = 200)
            } else {
                previa
            }
        }
        val respuesta = when {
            comando.servicioId in DatosFicticios.serviciosConEncargoVencido ->
                ResultadoElemento(comando.clientEventId, CommandStatus.DENEGADO, 403, codigo = "ALCANCE_VENCIDO")
            comando.tipo == CommandType.ASISTENCIA -> aplicarAsistencia(comando)
            else -> aplicado(comando)
        }
        inbox[comando.clientEventId] = respuesta
        return respuesta
    }

    private fun aplicarAsistencia(comando: ComandoSync): ResultadoElemento {
        val payload = SigehJson.decodeFromString(AsistenciaPayload.serializer(), comando.payloadJson)
        val actual = _registros.value[comando.programacionId]
        if (actual != null && actual.validado) {
            return if (actual.estado == payload.estado) {
                aplicado(comando)
            } else {
                ResultadoElemento(comando.clientEventId, CommandStatus.CONFLICTO, 409, codigo = "REGISTRO_VALIDADO")
            }
        }
        _registros.update { registros ->
            registros + (comando.programacionId to RegistroAsistenciaServidor(
                programacionId = comando.programacionId,
                estado = payload.estado,
                validado = false,
            ))
        }
        mutacionesPorJornada[comando.programacionId] = mutaciones(comando.programacionId) + 1
        return aplicado(comando)
    }

    private fun aplicado(comando: ComandoSync) = ResultadoElemento(
        clientEventId = comando.clientEventId,
        estado = CommandStatus.APLICADO,
        httpCode = 201,
        serverEventId = "srv-evt-${siguienteEvento++}",
        aplicadoEnServidorMillis = reloj.ahoraMillis(),
    )
}
