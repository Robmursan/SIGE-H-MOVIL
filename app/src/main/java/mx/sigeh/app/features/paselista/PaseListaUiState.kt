package mx.sigeh.app.features.paselista

import mx.sigeh.app.core.data.fake.DatosFicticios
import mx.sigeh.app.core.database.entity.aDominio
import mx.sigeh.app.core.model.AmbitoActivo
import mx.sigeh.app.core.model.AsistenciaPayload
import mx.sigeh.app.core.model.AvisoJornada
import mx.sigeh.app.core.model.ComandoLocal
import mx.sigeh.app.core.model.CommandStatus
import mx.sigeh.app.core.model.CommandType
import mx.sigeh.app.core.model.EstadoAsistencia
import mx.sigeh.app.core.model.EstadoPaseLista
import mx.sigeh.app.core.model.PaseLista
import mx.sigeh.app.core.model.SigehJson

/** Indicador por fila (prompt §6.4). "Sin capturar" no es "falta". */
enum class IndicadorFila { SINCRONIZADO, EN_TELEFONO, ENVIANDO, CONFLICTO, SIN_CAPTURAR, DENEGADO, EN_REVISION, NINGUNO }

data class FilaPaseUi(
    val programacionId: String,
    val nombre: String,
    val detalle: String,
    val aviso: AvisoJornada?,
    val exigible: Boolean,
    val estado: EstadoAsistencia,
    val indicador: IndicadorFila,
    val clientEventId: String?,
    val validadoEnServidor: Boolean = false,
)

data class ContadoresPase(
    val programados: Int = 0,
    val exigibles: Int = 0,
    val capturados: Int = 0,
    val pendientes: Int = 0,
    val validados: Int = 0,
)

data class PaseListaUiState(
    val cargando: Boolean = true,
    val sinProgramacion: Boolean = false,
    val contexto: String = "",
    val subcontexto: String = "",
    val enLinea: Boolean = false,
    val porEnviar: Int = 0,
    val filas: List<FilaPaseUi> = emptyList(),
    val contadores: ContadoresPase = ContadoresPase(),
    val puedeCapturar: Boolean = true,
    val mensaje: String? = null,
)

sealed interface PaseListaEvento {
    data object AbrirResumen : PaseListaEvento
}

/** Último comando de asistencia de cada jornada (el que manda en la fila). */
internal fun ultimoComandoPorJornada(comandos: List<ComandoLocal>): Map<String, ComandoLocal> =
    comandos
        .filter { it.tipo == CommandType.ASISTENCIA }
        .groupBy { it.programacionId }
        .mapValues { (_, lista) -> lista.maxBy { it.creadoEnMillis } }

/**
 * Une lo que tiene el servidor con la cola local. Nunca marca AUSENTE por omisión: sin captura,
 * la fila queda PENDIENTE ("Sin capturar").
 */
fun construirFilas(pase: PaseLista, comandos: List<ComandoLocal>): List<FilaPaseUi> {
    val ultimos = ultimoComandoPorJornada(comandos)
    return pase.jornadas.map { jornada ->
        if (!jornada.exigible) {
            return@map FilaPaseUi(
                programacionId = jornada.programacionId,
                nombre = jornada.nombre,
                detalle = listOfNotNull(jornada.categoria, jornada.motivoNoExigible).joinToString(" · "),
                aviso = jornada.aviso,
                exigible = false,
                estado = EstadoAsistencia.NO_EXIGIBLE,
                indicador = IndicadorFila.NINGUNO,
                clientEventId = null,
            )
        }
        val comando = ultimos[jornada.programacionId]
        val registro = pase.registros[jornada.programacionId]
        val (estado, indicador) = when {
            comando != null -> estadoLocal(comando) to indicadorPara(comando.estado)
            registro != null && registro.estado.esCaptura() -> registro.estado to IndicadorFila.SINCRONIZADO
            else -> EstadoAsistencia.PENDIENTE to IndicadorFila.SIN_CAPTURAR
        }
        val validado = registro?.validado == true && registro.estado == estado &&
            (comando == null || comando.estado.aplicado)
        FilaPaseUi(
            programacionId = jornada.programacionId,
            nombre = jornada.nombre,
            detalle = "${jornada.categoria} · ${jornada.horario} · RUD ···${jornada.rudUltimos4}",
            aviso = jornada.aviso,
            exigible = true,
            estado = estado,
            indicador = indicador,
            clientEventId = comando?.clientEventId,
            validadoEnServidor = validado,
        )
    }
}

/** Contadores en vivo. "Exigibles" excluye a quienes no requieren captura (vacaciones, convenio…). */
fun calcularContadores(filas: List<FilaPaseUi>): ContadoresPase {
    val exigibles = filas.filter { it.exigible }
    val capturados = exigibles.count { it.estado != EstadoAsistencia.PENDIENTE }
    return ContadoresPase(
        programados = filas.size,
        exigibles = exigibles.size,
        capturados = capturados,
        pendientes = exigibles.size - capturados,
        validados = exigibles.count { it.validadoEnServidor },
    )
}

internal fun contextoPase(ambito: AmbitoActivo): String =
    "${ambito.alcance.servicio.nombre} · ${ambito.turno.nombre} ${ambito.turno.horarioTexto}"

internal fun subcontextoPase(ambito: AmbitoActivo, pase: PaseLista?): String {
    val base = "${ambito.alcance.hospital.nombre} › ${ambito.alcance.servicio.divisionNombre}"
    return if (pase == null) base else "$base · ${pase.fechaCorta} · Programación v${pase.programacionVersion}"
}

private fun estadoLocal(comando: ComandoLocal): EstadoAsistencia =
    runCatching { SigehJson.decodeFromString(AsistenciaPayload.serializer(), comando.payloadJson).estado }
        .getOrDefault(EstadoAsistencia.PENDIENTE)

private fun indicadorPara(estado: CommandStatus): IndicadorFila = when (estado) {
    CommandStatus.PENDIENTE_LOCAL, CommandStatus.ERROR_REINTENTABLE -> IndicadorFila.EN_TELEFONO
    CommandStatus.ENVIANDO -> IndicadorFila.ENVIANDO
    CommandStatus.APLICADO, CommandStatus.YA_APLICADO -> IndicadorFila.SINCRONIZADO
    CommandStatus.CONFLICTO -> IndicadorFila.CONFLICTO
    CommandStatus.DENEGADO -> IndicadorFila.DENEGADO
    CommandStatus.REQUIERE_REVISION -> IndicadorFila.EN_REVISION
}

private fun EstadoAsistencia.esCaptura(): Boolean =
    this == EstadoAsistencia.PRESENTE || this == EstadoAsistencia.AUSENTE || this == EstadoAsistencia.PARCIAL

/** Pase y cola de ejemplo (prompt §6) para @Preview y pruebas de UI. */
internal fun paseDeEjemplo(): PaseLista = PaseLista(
    id = DatosFicticios.PASE_ID,
    programacionVersion = DatosFicticios.PROGRAMACION_VERSION,
    fechaCorta = DatosFicticios.FECHA_CORTA,
    estado = EstadoPaseLista.BORRADOR,
    jornadas = DatosFicticios.jornadas,
    registros = DatosFicticios.registrosServidorIniciales(),
    envioSolicitadoMillis = null,
)

internal fun comandosDeEjemplo(): List<ComandoLocal> = DatosFicticios.comandosIniciales().map { it.aDominio() }

internal fun paseListaEjemplo(enLinea: Boolean): PaseListaUiState {
    val comandos = comandosDeEjemplo()
    val filas = construirFilas(paseDeEjemplo(), comandos)
    return PaseListaUiState(
        cargando = false,
        contexto = "Medicina Interna · Matutino 07:00 – 14:30",
        subcontexto = "Hospital A › División de Medicina · Sáb 14 nov · Programación v3",
        enLinea = enLinea,
        porEnviar = comandos.count { it.estado.porEnviar },
        filas = filas,
        contadores = calcularContadores(filas),
    )
}
