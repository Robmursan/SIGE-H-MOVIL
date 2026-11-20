package mx.sigeh.app.core.auth

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mx.sigeh.app.core.model.Alcance
import mx.sigeh.app.core.model.AmbitoActivo

/**
 * Caché del ámbito en memoria: alcances vigentes y servicio/turno elegidos.
 * No se persiste; se limpia al cerrar sesión o al vencer el alcance.
 */
@Singleton
class ScopeCache @Inject constructor() {

    private val _alcances = MutableStateFlow<List<Alcance>>(emptyList())
    val alcances: StateFlow<List<Alcance>> = _alcances.asStateFlow()

    private val _activo = MutableStateFlow<AmbitoActivo?>(null)
    val activo: StateFlow<AmbitoActivo?> = _activo.asStateFlow()

    fun guardarAlcances(alcances: List<Alcance>) {
        _alcances.value = alcances
    }

    fun seleccionar(ambito: AmbitoActivo) {
        _activo.value = ambito
    }

    fun ambitoActual(): AmbitoActivo? = _activo.value

    fun limpiarAmbito() {
        _activo.value = null
    }

    fun limpiar() {
        _activo.value = null
        _alcances.value = emptyList()
    }
}
