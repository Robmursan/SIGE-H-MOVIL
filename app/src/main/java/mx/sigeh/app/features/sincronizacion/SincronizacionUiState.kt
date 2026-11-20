package mx.sigeh.app.features.sincronizacion

import mx.sigeh.app.core.common.FormatoHora
import mx.sigeh.app.core.designsystem.components.PresentacionEstado
import mx.sigeh.app.core.designsystem.components.presentacion
import mx.sigeh.app.core.model.AsistenciaPayload
import mx.sigeh.app.core.model.ComandoLocal
import mx.sigeh.app.core.model.CommandStatus
import mx.sigeh.app.core.model.CommandType
import mx.sigeh.app.core.model.DecisionConflicto
import mx.sigeh.app.core.model.IncidenciaPreliminarPayload
import mx.sigeh.app.core.model.ResolucionConflictoPayload
import mx.sigeh.app.core.model.SigehJson
import mx.sigeh.app.core.model.etiqueta

data class RegistroSyncUi(
    val clientEventId: String,
    val tipo: String,
    val titulo: String,
    val detalle: String,
    val estado: PresentacionEstado,
    val esConflicto: Boolean,
)

data class SincronizacionUiState(
    val cargando: Boolean = true,
    val enLinea: Boolean = false,
    val sinConexionDesde: String? = null,
    val ultimoEnvioCorrecto: String? = null,
    val porEnviar: Int = 0,
    val conConflicto: Int = 0,
    val aplicados: Int = 0,
    val sesionVencida: Boolean = false,
    val registros: List<RegistroSyncUi> = emptyList(),
    val mensaje: String? = null,
)

/**
 * Describe un comando de la cola para mostrarlo. "Aplicado" = el servidor lo recibió;
 * nunca se presenta como validación final de Supervisión.
 */
fun describirRegistro(
    comando: ComandoLocal,
    enLinea: Boolean,
    aplicadoEnMillis: Long?,
    servicioAjeno: String?,
): RegistroSyncUi {
    val hora = FormatoHora.hora(comando.creadoEnMillis)
    val tipoBase = when (comando.tipo) {
        CommandType.ASISTENCIA -> "ASISTENCIA"
        CommandType.INCIDENCIA_PRELIMINAR -> "INCIDENCIA PRELIMINAR"
        CommandType.RESOLUCION_CONFLICTO -> "RESOLUCIÓN DE CONFLICTO"
    }
    val verbo = when (comando.tipo) {
        CommandType.ASISTENCIA -> "Capturado"
        CommandType.INCIDENCIA_PRELIMINAR -> "Guardada"
        CommandType.RESOLUCION_CONFLICTO -> "Registrada"
    }
    val sesionVencida = comando.ultimoError == "HTTP_401" || comando.ultimoError == "HTTP_403"
    val detalle = when (comando.estado) {
        CommandStatus.CONFLICTO -> "Capturado a las $hora. El servidor tiene otra captura validada."
        CommandStatus.PENDIENTE_LOCAL, CommandStatus.ENVIANDO, CommandStatus.ERROR_REINTENTABLE -> when {
            sesionVencida -> "No se envió: tu sesión venció. Inicia sesión para reanudar el envío."
            comando.estado == CommandStatus.ERROR_REINTENTABLE && enLinea ->
                "No se pudo enviar (intento ${comando.intentos}). Se reintentará automáticamente."
            comando.tipo == CommandType.INCIDENCIA_PRELIMINAR -> "$verbo a las $hora · pendiente de autorizar en la web"
            enLinea || comando.estado == CommandStatus.ENVIANDO -> "$verbo a las $hora · enviándose"
            else -> "$verbo a las $hora · se enviará al volver la red"
        }
        CommandStatus.YA_APLICADO -> "Reenvío automático: el servidor ya la tenía y no la duplicó"
        CommandStatus.APLICADO -> {
            val recibido = FormatoHora.hora(aplicadoEnMillis ?: comando.creadoEnMillis)
            if (comando.tipo == CommandType.INCIDENCIA_PRELIMINAR) {
                "Recibida por el servidor a las $recibido · pendiente de autorizar en la web"
            } else {
                "Recibido por el servidor a las $recibido"
            }
        }
        CommandStatus.DENEGADO ->
            if (comando.ultimoError == "ALCANCE_VENCIDO") {
                "Tu encargo de turno en ese servicio venció antes del envío"
            } else {
                "El servidor lo rechazó: no tienes esa facultad en el ámbito"
            }
        CommandStatus.REQUIERE_REVISION -> "Pediste corrección a Supervisión. Supervisión decide."
    }
    return RegistroSyncUi(
        clientEventId = comando.clientEventId,
        tipo = if (servicioAjeno != null) "$tipoBase · ${servicioAjeno.uppercase()}" else tipoBase,
        titulo = tituloDe(comando),
        detalle = detalle,
        estado = comando.estado.presentacion(enLinea),
        esConflicto = comando.estado == CommandStatus.CONFLICTO,
    )
}

/** Conflictos primero; luego lo que falta enviar (más antiguo arriba); al final lo resuelto. */
fun ordenarRegistros(comandos: List<ComandoLocal>): List<ComandoLocal> {
    fun prioridad(estado: CommandStatus) = when (estado) {
        CommandStatus.CONFLICTO -> 0
        CommandStatus.PENDIENTE_LOCAL, CommandStatus.ENVIANDO, CommandStatus.ERROR_REINTENTABLE -> 1
        CommandStatus.REQUIERE_REVISION -> 2
        CommandStatus.APLICADO, CommandStatus.YA_APLICADO -> 3
        CommandStatus.DENEGADO -> 4
    }
    return comandos.sortedWith(
        compareBy<ComandoLocal> { prioridad(it.estado) }
            .thenBy { if (prioridad(it.estado) == 1) it.creadoEnMillis else -it.creadoEnMillis },
    )
}

private fun tituloDe(comando: ComandoLocal): String = runCatching {
    when (comando.tipo) {
        CommandType.ASISTENCIA -> {
            val p = SigehJson.decodeFromString(AsistenciaPayload.serializer(), comando.payloadJson)
            "${p.etiqueta} · ${p.estado.etiqueta()}"
        }
        CommandType.INCIDENCIA_PRELIMINAR -> {
            val p = SigehJson.decodeFromString(IncidenciaPreliminarPayload.serializer(), comando.payloadJson)
            val periodo = if (!p.jornadaCompleta && p.desde != null && p.hasta != null) " ${p.desde} – ${p.hasta}" else ""
            "${p.etiqueta} · ${p.tipoNombre}$periodo"
        }
        CommandType.RESOLUCION_CONFLICTO -> {
            val p = SigehJson.decodeFromString(ResolucionConflictoPayload.serializer(), comando.payloadJson)
            val decision = when (p.decision) {
                DecisionConflicto.CONSERVAR_SERVIDOR -> "Conservar lo del servidor"
                DecisionConflicto.SOLICITAR_CORRECCION -> "Solicitar corrección"
            }
            "${p.etiqueta} · $decision"
        }
    }
}.getOrDefault("Registro")
