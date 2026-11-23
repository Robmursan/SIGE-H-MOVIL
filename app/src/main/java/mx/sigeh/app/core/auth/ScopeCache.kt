package mx.sigeh.app.core.auth

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mx.sigeh.app.core.model.Alcance
import mx.sigeh.app.core.model.AmbitoActivo
import mx.sigeh.app.core.model.RolAlcance

/**
 * Servicio elegido en el selector (prompt M2 §9.2), salido de un contexto de `/auth/me/contextos`
 * o, como respaldo, de una asignación de `/auth/me` con servicio concreto. Con contextos los
 * nombres son los del servidor; con el respaldo vienen de `EtiquetasContexto` («Servicio ··· c91e»).
 */
data class ServicioActivo(
    val hospitalId: String,
    val divisionId: String?,
    val servicioId: String,
    val rol: RolAlcance?,
    val facultades: FacultadesAsistencia,
    val hospitalNombre: String,
    val divisionNombre: String?,
    val servicioNombre: String,
    /**
     * Zona IANA del hospital según `/auth/me/contextos`. Si se conoce, el pase la usa desde la primera
     * consulta; si es null, empieza con `America/Mexico_City` y adopta la que devuelva `/pases-lista`.
     */
    val zonaHoraria: String? = null,
    /** Fin de vigencia del contexto (ISO-8601), si el servidor lo informa. */
    val vigenteHasta: String? = null,
)

/**
 * Caché del ámbito en memoria. No se persiste; se limpia al cerrar sesión o al vencer el alcance.
 * - [servicio]: el servicio de M2 (pase en línea, programación y cuenta).
 * - [activo] y [alcances]: el ámbito del catálogo Fake de M0 con turno, que solo usan las
 *   pantallas detrás de `FUNCIONES_FUTURAS`.
 */
@Singleton
class ScopeCache @Inject constructor() {

    private val _servicio = MutableStateFlow<ServicioActivo?>(null)
    val servicio: StateFlow<ServicioActivo?> = _servicio.asStateFlow()

    private val _alcances = MutableStateFlow<List<Alcance>>(emptyList())
    val alcances: StateFlow<List<Alcance>> = _alcances.asStateFlow()

    private val _activo = MutableStateFlow<AmbitoActivo?>(null)
    val activo: StateFlow<AmbitoActivo?> = _activo.asStateFlow()

    fun seleccionarServicio(servicio: ServicioActivo) {
        _servicio.value = servicio
    }

    fun servicioActual(): ServicioActivo? = _servicio.value

    fun guardarAlcances(alcances: List<Alcance>) {
        _alcances.value = alcances
    }

    fun seleccionar(ambito: AmbitoActivo) {
        _activo.value = ambito
    }

    fun ambitoActual(): AmbitoActivo? = _activo.value

    fun limpiarAmbito() {
        _activo.value = null
        _servicio.value = null
    }

    fun limpiar() {
        _servicio.value = null
        _activo.value = null
        _alcances.value = emptyList()
    }
}
