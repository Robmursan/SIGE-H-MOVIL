package mx.sigeh.app.core.network

/**
 * Configuración de red. La URL base y el interruptor de IAM vienen de BuildConfig
 * (`sigeh.api.baseUrl` y `sigeh.api.iam` en local.properties o como propiedad de Gradle).
 * Sin URL base la app funciona completa en modo Fake.
 */
class NetworkConfig(urlBase: String, private val iamRealSolicitado: Boolean) {

    /** Retrofit exige que la URL base termine en "/" (por ejemplo `https://qa.ejemplo/api/v1/`). */
    val baseUrl: String = urlBase.trim().let { if (it.isEmpty() || it.endsWith("/")) it else "$it/" }

    val configurada: Boolean get() = baseUrl.isNotBlank()

    /** IAM real solo si se pidió explícitamente y hay URL base. */
    val iamReal: Boolean get() = iamRealSolicitado && configurada
}
