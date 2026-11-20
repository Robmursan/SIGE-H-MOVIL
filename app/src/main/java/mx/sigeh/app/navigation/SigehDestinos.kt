package mx.sigeh.app.navigation

import kotlinx.serialization.Serializable
import mx.sigeh.app.core.designsystem.components.SigehTab

/**
 * Rutas tipadas (Navigation Compose + kotlinx.serialization).
 *
 * Flujo de M2: Login → SelectorAmbito → Principal (Pase de lista · Programación · Cuenta).
 * Los destinos marcados «futuro» solo se registran con `FUNCIONES_FUTURAS` (prompt M2 §7.4).
 */
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

    /** Solo con `PROGRAMACION_CONSULTAR` en el servicio (en Fake siempre). */
    @Serializable
    data object Programacion : SigehDestino

    @Serializable
    data object Cuenta : SigehDestino

    /** Detalle de una jornada del pase o de su acuse; sin barra inferior. */
    @Serializable
    data class DetalleJornada(val programacionId: String) : SigehDestino

    // --- Futuro (M0 offline, B7 del backend) -------------------------------------------------

    @Serializable
    data object Incidencias : SigehDestino

    @Serializable
    data object Movimientos : SigehDestino

    /** Agenda propia de M0 (copia offline de la programación). */
    @Serializable
    data object Agenda : SigehDestino

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
        SigehTab.PROGRAMACION -> SigehDestino.Programacion
        SigehTab.CUENTA -> SigehDestino.Cuenta
    }

/**
 * Pestañas visibles (prompt M2 §7.4): Pase de lista · Programación (solo con permiso) · Cuenta.
 * Con `FUNCIONES_FUTURAS` se agregan Incidencias y Movimientos de M0.
 */
fun pestanasVisibles(funcionesFuturas: Boolean, programacion: Boolean): List<SigehTab> = buildList {
    add(SigehTab.PASE)
    if (funcionesFuturas) {
        add(SigehTab.INCIDENCIAS)
        add(SigehTab.MOVIMIENTOS)
    }
    if (programacion) add(SigehTab.PROGRAMACION)
    add(SigehTab.CUENTA)
}
