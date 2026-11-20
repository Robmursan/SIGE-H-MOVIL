package mx.sigeh.app.core.designsystem.components

import mx.sigeh.app.core.designsystem.theme.TonoEstado
import mx.sigeh.app.core.model.CommandStatus

/**
 * Texto y tono con que se muestra un comando local. Ningún texto presenta una captura del
 * teléfono como validación final: "Aplicado" significa que el servidor la recibió, no que
 * Supervisión la haya validado.
 */
data class PresentacionEstado(val texto: String, val tono: TonoEstado)

fun CommandStatus.presentacion(enLinea: Boolean): PresentacionEstado = when (this) {
    CommandStatus.PENDIENTE_LOCAL ->
        if (enLinea) PresentacionEstado("Enviando…", TonoEstado.ADVERTENCIA)
        else PresentacionEstado("En el teléfono", TonoEstado.ADVERTENCIA)
    CommandStatus.ENVIANDO -> PresentacionEstado("Enviando…", TonoEstado.ADVERTENCIA)
    CommandStatus.ERROR_REINTENTABLE ->
        if (enLinea) PresentacionEstado("Reintentará", TonoEstado.ADVERTENCIA)
        else PresentacionEstado("En el teléfono", TonoEstado.ADVERTENCIA)
    CommandStatus.APLICADO -> PresentacionEstado("Aplicado", TonoEstado.POSITIVO)
    CommandStatus.YA_APLICADO -> PresentacionEstado("Ya aplicado", TonoEstado.INFORMATIVO)
    CommandStatus.CONFLICTO -> PresentacionEstado("Conflicto", TonoEstado.NEGATIVO)
    CommandStatus.DENEGADO -> PresentacionEstado("Denegado", TonoEstado.NEUTRO)
    CommandStatus.REQUIERE_REVISION -> PresentacionEstado("En revisión", TonoEstado.INFORMATIVO)
}
