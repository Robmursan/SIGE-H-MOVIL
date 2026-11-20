package mx.sigeh.app.features.movimientos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.sigeh.app.core.auth.ScopeCache
import mx.sigeh.app.core.common.FormatoHora
import mx.sigeh.app.core.common.Reloj
import mx.sigeh.app.core.data.MovimientosRepository
import mx.sigeh.app.core.data.PaseListaRepository
import mx.sigeh.app.core.sync.ConnectivityObserver

/** Pase de salida (CU-MOV-14/15/16). El regreso es opcional y nunca se inventa la hora. */
@HiltViewModel
class PaseSalidaViewModel @Inject constructor(
    private val scopeCache: ScopeCache,
    private val paseListaRepository: PaseListaRepository,
    private val movimientosRepository: MovimientosRepository,
    private val reloj: Reloj,
    conectividad: ConnectivityObserver,
) : ViewModel() {

    private val formulario = MutableStateFlow(PaseSalidaUiState())

    private val _eventos = Channel<PaseSalidaEvento>(Channel.BUFFERED)
    val eventos: Flow<PaseSalidaEvento> = _eventos.receiveAsFlow()

    val uiState: StateFlow<PaseSalidaUiState> = combine(formulario, conectividad.enLinea) { estado, enLinea ->
        estado.copy(enLinea = enLinea)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PaseSalidaUiState())

    init {
        viewModelScope.launch {
            val ambito = scopeCache.ambitoActual()
            val pase = ambito?.let { paseListaRepository.observarPase(it.alcance.servicio.id, it.turno.id).first() }
            val jornadas = pase?.jornadas.orEmpty().filter { it.exigible }
            val conPase = jornadas.associateWith { movimientosRepository.paseAutorizadoHoy(it.programacionId) }
            // Primero quien tiene un pase autorizado hoy (en el mockup, Daniela Cruz V.).
            val trabajadores = jornadas
                .sortedBy { conPase[it] == null }
                .map { TrabajadorProgramado(it.programacionId, "${it.nombre} · ${it.categoria}") }
            val primero = trabajadores.firstOrNull()
            val ahora = FormatoHora.hora(reloj.ahoraMillis())
            formulario.update {
                it.copy(
                    cargando = false,
                    trabajadores = trabajadores,
                    seleccionado = primero,
                    paseAutorizado = primero?.let { t -> movimientosRepository.paseAutorizadoHoy(t.programacionId) },
                    horaSalida = ahora,
                    horaRegreso = ahora,
                    puedeRegistrar = ambito?.facultades?.registrarSalida ?: false,
                )
            }
        }
    }

    fun onTrabajador(trabajador: TrabajadorProgramado) {
        formulario.update { it.copy(seleccionado = trabajador, paseAutorizado = null, error = null) }
        viewModelScope.launch {
            val pase = movimientosRepository.paseAutorizadoHoy(trabajador.programacionId)
            formulario.update { if (it.seleccionado == trabajador) it.copy(paseAutorizado = pase) else it }
        }
    }

    fun onHoraSalida(hora: String) = formulario.update { it.copy(horaSalida = hora) }

    fun onAhora() = formulario.update { it.copy(horaSalida = FormatoHora.hora(reloj.ahoraMillis())) }

    fun onRegistrarRegreso(activo: Boolean) = formulario.update { it.copy(registrarRegreso = activo) }

    fun onHoraRegreso(hora: String) = formulario.update { it.copy(horaRegreso = hora) }

    fun onNota(texto: String) = formulario.update { it.copy(nota = texto.take(MAX_NOTA)) }

    fun onGuardar() {
        val estado = uiState.value
        val trabajador = estado.seleccionado ?: return
        if (!estado.puedeGuardar) return
        formulario.update { it.copy(guardando = true, error = null) }
        viewModelScope.launch {
            val ok = movimientosRepository.registrarSalida(
                programacionId = trabajador.programacionId,
                horaSalida = estado.horaSalida,
                horaRegreso = estado.horaRegreso.takeIf { estado.registrarRegreso },
                nota = estado.nota.trim().takeIf { it.isNotEmpty() },
            )
            formulario.update {
                it.copy(guardando = false, error = if (ok) null else "No se pudo registrar la salida. Inténtalo de nuevo.")
            }
            if (ok) _eventos.send(PaseSalidaEvento.Registrada)
        }
    }

    private companion object {
        const val MAX_NOTA = 120
    }
}
