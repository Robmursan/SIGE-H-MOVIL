package mx.sigeh.app.core.data

/** OPS: estado del servidor para mostrarlo en la app (CU-OPS-03, `GET /meta/version`). */
interface ServidorRepository {
    /** true si la app habla con el backend real (IAM V2); false en modo Fake. */
    val modoReal: Boolean

    /** Versión del contrato publicada por el servidor, o null si no respondió. */
    suspend fun versionContrato(): String?
}
