package mx.sigeh.app.features.detallejornada

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import mx.sigeh.app.core.auth.ScopeCache
import mx.sigeh.app.core.auth.SessionStore
import mx.sigeh.app.core.common.HoraHospital
import mx.sigeh.app.core.data.PaseListaRepository
import mx.sigeh.app.core.designsystem.components.apariencia
import mx.sigeh.app.core.model.EstadoAsistencia
import mx.sigeh.app.core.model.EstadoProgramacion
import mx.sigeh.app.features.paselista.autorCaptura
import mx.sigeh.app.features.paselista.texto

data class DatoDetalle(val etiqueta: String, val valor: String)

data class DetalleJornadaUiState(
    val encontrada: Boolean = false,
    val nombre: String = "",
    val servicio: String = "",
    val estadoOperativo: EstadoAsistencia? = null,
    val estadoProgramacion: EstadoProgramacion? = null,
    val programacion: List<DatoDetalle> = emptyList(),
    val asistencia: List<DatoDetalle> = emptyList(),
    /** Null si aún no hay captura registrada. */
    val acuse: List<DatoDetalle>? = null,
)

/**
 * Pantalla 9.7 · Detalle de jornada. Toma los datos de la última página del pase en memoria y del
 * acuse (caché del repositorio por `programacionId`). **No** llama a otro endpoint.
 */
@HiltViewModel
class DetalleJornadaViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repositorio: PaseListaRepository,
    private val scopeCache: ScopeCache,
    sessionStore: SessionStore,
) : ViewModel() {

    private val programacionId: String = checkNotNull(savedState[ARG_PROGRAMACION_ID]) { "Falta programacionId" }

    val uiState: StateFlow<DetalleJornadaUiState> = sessionStore.session
        .map { construir(it?.usuarioId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), construir(sessionStore.sesionEnMemoria()?.usuarioId))

    private fun construir(usuarioId: String?): DetalleJornadaUiState {
        val jornada = repositorio.jornada(programacionId)
        val acuse = repositorio.acuse(programacionId)
        if (jornada == null && acuse == null) return DetalleJornadaUiState(encontrada = false)
        val zona = repositorio.zonaHoraria() ?: HoraHospital.ZONA_POR_DEFECTO

        // El acuse es lo más reciente que confirmó el servidor; la jornada recargada lo incluye.
        val estado = jornada?.takeIf { it.capturada }?.estadoOperativo ?: acuse?.estado?.aEstadoAsistencia() ?: jornada?.estadoOperativo
        val validacion = jornada?.validacion ?: acuse?.validacion
        val revision = jornada?.revision?.takeIf { it != "0" } ?: acuse?.revision ?: jornada?.revision
        val actorId = acuse?.actorId ?: jornada?.ultimaCaptura?.actorId
        val registradoEn = acuse?.registradoEn ?: jornada?.ultimaCaptura?.registradoEn
        val eventoId = acuse?.eventoId ?: jornada?.ultimaCaptura?.eventoId

        return DetalleJornadaUiState(
            encontrada = true,
            nombre = jornada?.nombre ?: "Jornada registrada",
            servicio = scopeCache.servicioActual()?.servicioNombre.orEmpty(),
            estadoOperativo = estado,
            estadoProgramacion = jornada?.estadoProgramacion,
            programacion = jornada?.let {
                listOf(
                    DatoDetalle("Turno", it.turnoCodigo ?: "Sin turno"),
                    DatoDetalle("Inicio", HoraHospital.hora(it.inicioProgramado, zona)),
                    DatoDetalle("Fin", HoraHospital.hora(it.finProgramado, zona)),
                    DatoDetalle("Estado de la programación", it.estadoProgramacion.apariencia().texto),
                )
            }.orEmpty(),
            asistencia = listOfNotNull(
                estado?.let { DatoDetalle("Estado operativo", it.apariencia(capturable = jornada?.capturable == true).texto) },
                DatoDetalle("Validación administrativa", validacion?.texto()?.replaceFirstChar { it.uppercase() } ?: "Sin captura"),
                revision?.let { DatoDetalle("Revisión", it) },
            ),
            acuse = if (actorId != null && registradoEn != null && eventoId != null) {
                listOf(
                    DatoDetalle("Capturó", autorCaptura(actorId, usuarioId)),
                    DatoDetalle("Registrado", "${HoraHospital.horaConSegundos(registradoEn, zona)} (hora del servidor)"),
                    DatoDetalle("Evento", eventoId),
                )
            } else {
                null
            },
        )
    }

    companion object {
        /** Nombre del argumento de `SigehDestino.DetalleJornada`. */
        const val ARG_PROGRAMACION_ID = "programacionId"
    }
}
