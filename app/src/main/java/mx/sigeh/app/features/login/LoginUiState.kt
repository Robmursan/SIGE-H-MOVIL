package mx.sigeh.app.features.login

data class LoginUiState(
    val usuario: String = "",
    val contrasena: String = "",
    val cargando: Boolean = false,
    val error: String? = null,
    /** Pie: "versión de prueba" en modo Fake; versión del contrato o estado del servidor en modo real. */
    val pieServidor: String = PIE_MODO_FAKE,
) {
    val puedeEnviar: Boolean get() = usuario.isNotBlank() && contrasena.isNotBlank() && !cargando
}

const val PIE_MODO_FAKE = "Hospital A · versión de prueba"

sealed interface LoginEvento {
    data object SesionIniciada : LoginEvento
}
