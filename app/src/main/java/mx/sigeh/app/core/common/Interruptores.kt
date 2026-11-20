package mx.sigeh.app.core.common

/**
 * Interruptores de producto que no dependen de la red. Vienen de BuildConfig y se inyectan para
 * poder probarlos sin recompilar.
 *
 * @property funcionesFuturas `sigeh.features.futuras`. Con `false` (por defecto) se ocultan las
 *   funciones sin backend: incidencias, movimientos y pase de salida, recepción de eventual,
 *   resumen y envío del pase, sincronización y conflictos. Su código (cola offline de M0) se
 *   conserva para el bloque B7: no se elimina.
 */
data class Interruptores(
    val funcionesFuturas: Boolean,
)
