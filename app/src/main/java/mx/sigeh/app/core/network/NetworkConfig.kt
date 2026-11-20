package mx.sigeh.app.core.network

/**
 * Configuración de red. La URL base y los interruptores vienen de BuildConfig
 * (`sigeh.api.baseUrl`, `sigeh.api.iam` y `sigeh.api.asistencia` en local.properties o como
 * propiedad de Gradle). Sin URL base la app funciona completa en modo Fake.
 */
class NetworkConfig(
    urlBase: String,
    private val iamRealSolicitado: Boolean,
    private val asistenciaRealSolicitada: Boolean = false,
) {

    /** Retrofit exige que la URL base termine en "/" (por ejemplo `https://qa.ejemplo/api/v1/`). */
    val baseUrl: String = urlBase.trim().let { if (it.isEmpty() || it.endsWith("/")) it else "$it/" }

    val configurada: Boolean get() = baseUrl.isNotBlank()

    /** IAM real solo si se pidió explícitamente y hay URL base. */
    val iamReal: Boolean get() = iamRealSolicitado && configurada

    /**
     * Pase de lista y captura reales. Exigen IAM real: sin tokens del servidor todas las rutas de
     * asistencia responderían 401. Lo contrario (IAM real con asistencia Fake) sí está permitido.
     */
    val asistenciaReal: Boolean get() = asistenciaRealSolicitada && iamReal
}
