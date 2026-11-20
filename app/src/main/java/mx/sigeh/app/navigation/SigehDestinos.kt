package mx.sigeh.app.navigation

import kotlinx.serialization.Serializable
import mx.sigeh.app.core.designsystem.components.SigehTab

/** Rutas tipadas (Navigation Compose + kotlinx.serialization). */
sealed interface SigehDestino {

    @Serializable
    data class Login(val sesionVencida: Boolean = false) : SigehDestino

    @Serializable
    data class SelectorAmbito(val alcanceVencido: Boolean = false) : SigehDestino

    /** Grafo principal con barra inferior. */
    @Serializable
    data object Principal : SigehDestino

    @Serializable
    data object PaseLista : SigehDestino

    @Serializable
    data object Incidencias : SigehDestino

    @Serializable
    data object Movimientos : SigehDestino

    @Serializable
    data object Agenda : SigehDestino

    // Destinos de detalle (sin barra inferior).

    @Serializable
    data class RegistrarIncidencia(val jornadaId: String) : SigehDestino

    @Serializable
    data object ResumenPase : SigehDestino

    @Serializable
    data object PaseSalida : SigehDestino

    @Serializable
    data class RecepcionEventual(val asignacionId: String) : SigehDestino

    @Serializable
    data object Sincronizacion : SigehDestino

    @Serializable
    data class Conflicto(val clientEventId: String) : SigehDestino
}

/** Destino de cada pestaña de la barra inferior. */
internal val SigehTab.destino: SigehDestino
    get() = when (this) {
        SigehTab.PASE -> SigehDestino.PaseLista
        SigehTab.INCIDENCIAS -> SigehDestino.Incidencias
        SigehTab.MOVIMIENTOS -> SigehDestino.Movimientos
        SigehTab.AGENDA -> SigehDestino.Agenda
    }
