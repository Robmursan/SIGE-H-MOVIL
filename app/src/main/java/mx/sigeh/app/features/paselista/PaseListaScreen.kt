package mx.sigeh.app.features.paselista

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import mx.sigeh.app.core.designsystem.components.AcuseSnackbarVisuals
import mx.sigeh.app.core.designsystem.components.AvatarIniciales
import mx.sigeh.app.core.designsystem.components.BannerSinConexion
import mx.sigeh.app.core.designsystem.components.BloqueoNoHabilitado
import mx.sigeh.app.core.designsystem.components.BloqueoSesionTerminada
import mx.sigeh.app.core.designsystem.components.BloqueoSinPermiso
import mx.sigeh.app.core.designsystem.components.BotonesCaptura
import mx.sigeh.app.core.designsystem.components.CargandoPantalla
import mx.sigeh.app.core.designsystem.components.ConfirmarCapturaSheet
import mx.sigeh.app.core.designsystem.components.EstadoAsistenciaBadge
import mx.sigeh.app.core.designsystem.components.EstadoBloqueo
import mx.sigeh.app.core.designsystem.components.AccionBloqueo
import mx.sigeh.app.core.designsystem.components.EstadoVacio
import mx.sigeh.app.core.designsystem.components.InfoCallout
import mx.sigeh.app.core.designsystem.components.ProgresoPagina
import mx.sigeh.app.core.designsystem.components.SigehOutlinedButton
import mx.sigeh.app.core.designsystem.components.SigehTopBar
import mx.sigeh.app.core.designsystem.components.SnackbarAcuse
import mx.sigeh.app.core.designsystem.icon.SigehIcons
import mx.sigeh.app.core.designsystem.theme.SigehColors
import mx.sigeh.app.core.designsystem.theme.SigehShapes
import mx.sigeh.app.core.designsystem.theme.SigehTheme
import mx.sigeh.app.core.designsystem.theme.TonoEstado
import mx.sigeh.app.core.model.EstadoAsistencia
import mx.sigeh.app.core.model.EstadoCaptura
import mx.sigeh.app.core.model.EstadoProgramacion

/** Acciones de la pantalla del pase, agrupadas para no pasar quince lambdas sueltas. */
data class AccionesPaseLista(
    val onCambiarServicio: () -> Unit = {},
    val onActualizar: () -> Unit = {},
    val onDiaAnterior: () -> Unit = {},
    val onDiaSiguiente: () -> Unit = {},
    val onHoy: () -> Unit = {},
    val onFechaSeleccionada: (String) -> Unit = {},
    val onTurnoSeleccionado: (String?) -> Unit = {},
    val onAbrirCaptura: (String, EstadoCaptura) -> Unit = { _, _ -> },
    val onAbrirDetalle: (String) -> Unit = {},
    val onCargarMas: () -> Unit = {},
    val onEstadoHoja: (EstadoCaptura) -> Unit = {},
    val onConfirmar: (EstadoCaptura) -> Unit = {},
    val onCancelarHoja: () -> Unit = {},
    val onReintentar: () -> Unit = {},
    val onCerrarDialogo: () -> Unit = {},
    val onIniciarSesion: () -> Unit = {},
)

/** Pantalla 9.3 · Pase de lista en línea (con 9.4 confirmación, 9.5 acuse y 9.6 errores). */
@Composable
fun PaseListaScreen(
    estado: PaseListaUiState,
    acciones: AccionesPaseLista,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(estado.sinServicio) { if (estado.sinServicio) acciones.onCambiarServicio() }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { datos ->
                if (datos.visuals is AcuseSnackbarVisuals) SnackbarAcuse(datos) else Snackbar(datos)
            }
        },
        topBar = { BarraPase(estado, acciones) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            BannerSinConexion(visible = !estado.enLinea)
            when {
                estado.bloqueo != null -> Bloqueo(estado.bloqueo, acciones, Modifier.weight(1f))
                estado.cargando -> CargandoPantalla(Modifier.weight(1f))
                else -> Lista(estado, acciones, Modifier.weight(1f))
            }
        }
    }

    estado.hoja?.let { hoja ->
        ConfirmarCapturaSheet(
            nombre = hoja.nombre,
            detalle = hoja.detalle,
            estado = hoja.estado,
            onEstadoChange = acciones.onEstadoHoja,
            enviando = hoja.enviando,
            onConfirmar = acciones.onConfirmar,
            onCancelar = acciones.onCancelarHoja,
        )
    }
    estado.dialogo?.let { DialogoError(it, acciones) }
}

@Composable
private fun BarraPase(estado: PaseListaUiState, acciones: AccionesPaseLista) {
    var elegirFecha by rememberSaveable { mutableStateOf(false) }
    SigehTopBar(
        titulo = estado.servicio,
        subtitulo = estado.subtitulo,
        acciones = {
            TextButton(
                onClick = acciones.onCambiarServicio,
                colors = ButtonDefaults.textButtonColors(contentColor = SigehColors.SobreMarca),
                modifier = Modifier.testTag("pase_cambiar_servicio"),
            ) { Text("Cambiar", style = MaterialTheme.typography.labelLarge) }
        },
        contenidoInferior = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = acciones.onDiaAnterior) {
                    Icon(SigehIcons.ChevronLeft, contentDescription = "Día anterior", tint = SigehColors.Tinta)
                }
                TextButton(onClick = { elegirFecha = true }, modifier = Modifier.weight(1f).testTag("pase_fecha")) {
                    Icon(SigehIcons.Calendar, contentDescription = null, modifier = Modifier.size(18.dp), tint = SigehColors.Primary)
                    Spacer(Modifier.size(6.dp))
                    Text(estado.fechaTexto, style = MaterialTheme.typography.labelLarge, color = SigehColors.OnSurface)
                }
                IconButton(onClick = acciones.onDiaSiguiente) {
                    Icon(SigehIcons.ChevronRight, contentDescription = "Día siguiente", tint = SigehColors.Tinta)
                }
                if (!estado.esHoy) TextButton(onClick = acciones.onHoy) { Text("Hoy") }
                IconButton(onClick = acciones.onActualizar, modifier = Modifier.testTag("pase_actualizar")) {
                    Icon(SigehIcons.RefreshCw, contentDescription = "Actualizar", tint = SigehColors.Primary)
                }
            }
            if (estado.turnos.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ChipTurnoPase("Todos", estado.horarioSeleccionado == null) { acciones.onTurnoSeleccionado(null) }
                    estado.turnos.forEach { turno ->
                        ChipTurnoPase(turno.turnoCodigo, estado.horarioSeleccionado == turno.horarioId) {
                            acciones.onTurnoSeleccionado(turno.horarioId)
                        }
                    }
                }
            }
        },
    )
    if (elegirFecha) {
        SelectorFecha(fecha = estado.fecha, onElegir = { acciones.onFechaSeleccionada(it); elegirFecha = false }, onCerrar = { elegirFecha = false })
    }
}

@Composable
private fun ChipTurnoPase(texto: String, seleccionado: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = seleccionado,
        onClick = onClick,
        label = { Text(texto) },
        leadingIcon = if (seleccionado) {
            { Icon(SigehIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
        } else {
            null
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = SigehColors.PrimaryContainer,
            selectedLabelColor = SigehColors.OnPrimaryContainer,
        ),
        modifier = Modifier.testTag("turno_$texto"),
    )
}

/** El DatePicker trabaja en UTC a medianoche: se traduce a y desde `YYYY-MM-DD` sin zonas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectorFecha(fecha: String, onElegir: (String) -> Unit, onCerrar: () -> Unit) {
    val formato = remember { SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") } }
    val estadoFecha = rememberDatePickerState(initialSelectedDateMillis = runCatching { formato.parse(fecha)?.time }.getOrNull())
    DatePickerDialog(
        onDismissRequest = onCerrar,
        confirmButton = {
            TextButton(onClick = { estadoFecha.selectedDateMillis?.let { onElegir(formato.format(Date(it))) } ?: onCerrar() }) {
                Text("Aceptar")
            }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
    ) { DatePicker(state = estadoFecha) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Lista(estado: PaseListaUiState, acciones: AccionesPaseLista, modifier: Modifier) {
    PullToRefreshBox(isRefreshing = estado.refrescando, onRefresh = acciones.onActualizar, modifier = modifier) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("pase_lista"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (estado.errorCarga) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoCallout(
                            texto = "No se pudo cargar el pase de lista. Revisa la conexión y vuelve a intentar.",
                            tono = TonoEstado.NEGATIVO,
                        )
                        SigehOutlinedButton("Reintentar", onClick = acciones.onActualizar)
                    }
                }
            }
            if (estado.filas.isNotEmpty()) {
                item { ProgresoPagina(estado.capturadas, estado.capturables, estado.conteos) }
            }
            if (estado.vacio) {
                item { EstadoVacio("No hay jornadas programadas para este día.") }
            }
            items(estado.filas, key = { it.programacionId }) { fila ->
                FilaPase(fila, estado.enLinea, acciones)
            }
            if (estado.hayMas) {
                item {
                    SigehOutlinedButton(
                        texto = if (estado.cargandoMas) "Cargando…" else "Cargar más",
                        onClick = acciones.onCargarMas,
                        habilitado = !estado.cargandoMas,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pase_cargar_mas"),
                    )
                }
            }
        }
    }
}

@Composable
private fun FilaPase(fila: FilaJornada, enLinea: Boolean, acciones: AccionesPaseLista) {
    val borde = if (fila.resaltada) SigehColors.Acento else SigehColors.OutlineVariant
    val fondo = if (fila.resaltada) SigehColors.AcentoSuave else Color.White
    val alAbrir: (() -> Unit)? = if (fila.tipo == TipoFila.CAPTURADA) ({ acciones.onAbrirDetalle(fila.programacionId) }) else null
    val contenido: @Composable () -> Unit = {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AvatarIniciales(fila.nombre)
                Column(Modifier.weight(1f)) {
                    Text(fila.nombre, style = MaterialTheme.typography.titleSmall, color = SigehColors.OnSurface)
                    Text(fila.detalle, style = MaterialTheme.typography.bodySmall, color = SigehColors.OnSurfaceVariant)
                }
                BadgeFila(fila)
            }
            when (fila.tipo) {
                TipoFila.CAPTURABLE -> BotonesCaptura(
                    onPresente = { acciones.onAbrirCaptura(fila.programacionId, EstadoCaptura.PRESENTE) },
                    onAusente = { acciones.onAbrirCaptura(fila.programacionId, EstadoCaptura.AUSENTE) },
                    onParcial = { acciones.onAbrirCaptura(fila.programacionId, EstadoCaptura.PARCIAL) },
                    enabled = enLinea,
                    nombre = fila.nombre,
                )
                TipoFila.CAPTURADA -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        fila.lineaCaptura.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = SigehColors.Tinta,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(SigehIcons.ChevronRight, contentDescription = null, tint = SigehColors.OnSurfaceVariant, modifier = Modifier.size(18.dp))
                }
                TipoFila.NO_CAPTURABLE -> fila.motivo?.let { motivo ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(SigehIcons.Info, contentDescription = null, tint = SigehColors.OnSurfaceVariant, modifier = Modifier.size(16.dp))
                        Text(motivo, style = MaterialTheme.typography.bodySmall, color = SigehColors.OnSurfaceVariant)
                    }
                }
                TipoFila.ENVIANDO -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.semantics(mergeDescendants = true) {},
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = SigehColors.Primary)
                    Text("Enviando…", style = MaterialTheme.typography.bodySmall, color = SigehColors.Tinta)
                }
            }
        }
    }
    val modificador = Modifier
        .fillMaxWidth()
        .testTag("fila_${fila.programacionId}")
    if (alAbrir != null) {
        Surface(onClick = alAbrir, shape = SigehShapes.Tarjeta, color = fondo, border = BorderStroke(1.dp, borde), modifier = modificador) { contenido() }
    } else {
        Surface(shape = SigehShapes.Tarjeta, color = fondo, border = BorderStroke(1.dp, borde), modifier = modificador) { contenido() }
    }
}

@Composable
private fun BadgeFila(fila: FilaJornada) {
    when (fila.tipo) {
        TipoFila.CAPTURABLE, TipoFila.ENVIANDO -> EstadoAsistenciaBadge(EstadoAsistencia.PENDIENTE, capturable = true)
        TipoFila.CAPTURADA -> EstadoAsistenciaBadge(fila.estadoOperativo)
        TipoFila.NO_CAPTURABLE ->
            if (fila.estadoProgramacion != EstadoProgramacion.PROGRAMADA) {
                EstadoAsistenciaBadge(fila.estadoProgramacion)
            } else {
                EstadoAsistenciaBadge(fila.estadoOperativo, capturable = false)
            }
    }
}

@Composable
private fun Bloqueo(bloqueo: BloqueoPase, acciones: AccionesPaseLista, modifier: Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        when (bloqueo) {
            BloqueoPase.SIN_PERMISO -> BloqueoSinPermiso(onElegirServicio = acciones.onCambiarServicio)
            BloqueoPase.CONTEXTO_NO_DISPONIBLE -> EstadoBloqueo(
                icono = SigehIcons.ShieldX,
                titulo = "Servicio no disponible",
                texto = "Este servicio ya no está disponible para tu sesión. Elige otro de tus ámbitos autorizados.",
                accion = AccionBloqueo("Elegir servicio", acciones.onCambiarServicio),
                tono = TonoEstado.NEGATIVO,
            )
            BloqueoPase.NO_HABILITADO -> BloqueoNoHabilitado(onReintentar = acciones.onActualizar)
            BloqueoPase.SESION_TERMINADA -> BloqueoSesionTerminada(onIniciarSesion = acciones.onIniciarSesion)
        }
    }
}

@Composable
private fun DialogoError(dialogo: DialogoCaptura, acciones: AccionesPaseLista) {
    val (titulo, texto) = when (dialogo) {
        is DialogoCaptura.Incierta -> "No pudimos confirmar el registro" to
            "Puede que sí se haya guardado. Al reintentar se envía la misma operación, así que no se duplica."
        is DialogoCaptura.YaRegistrada -> "Esta jornada ya tenía registro" to
            "Otra persona ya registró esta jornada. La lista se actualizó con lo que tiene el servidor."
        DialogoCaptura.FueraDeHorario -> "Fuera del horario de captura" to
            "El servidor ya no admite capturar esta jornada en este momento."
        is DialogoCaptura.NoAdmiteCaptura -> "La jornada no admite captura" to dialogo.motivo
        DialogoCaptura.NoDisponible -> "La jornada ya no está disponible" to
            "La programación cambió. La lista se actualizó."
        DialogoCaptura.NoHabilitada -> "Pase de lista no disponible en este servidor" to
            "El registro de asistencia no está habilitado. Avisa a Coordinación o vuelve a intentar más tarde."
        DialogoCaptura.ErrorInterno -> "No se pudo registrar" to
            "Ocurrió un error en la aplicación. La lista se actualizó; vuelve a intentarlo."
    }
    AlertDialog(
        onDismissRequest = acciones.onCerrarDialogo,
        icon = {
            Icon(
                if (dialogo is DialogoCaptura.Incierta) SigehIcons.CloudOff else SigehIcons.AlertTriangle,
                contentDescription = null,
            )
        },
        title = { Text(titulo) },
        text = { Text(texto) },
        confirmButton = {
            when (dialogo) {
                is DialogoCaptura.Incierta ->
                    TextButton(onClick = acciones.onReintentar, modifier = Modifier.testTag("dialogo_reintentar")) { Text("Reintentar") }
                is DialogoCaptura.YaRegistrada -> TextButton(onClick = {
                    acciones.onCerrarDialogo()
                    acciones.onAbrirDetalle(dialogo.programacionId)
                }) { Text("Ver registro") }
                else -> TextButton(onClick = acciones.onCerrarDialogo) { Text("Entendido") }
            }
        },
        dismissButton = when (dialogo) {
            is DialogoCaptura.Incierta -> {
                { TextButton(onClick = acciones.onCerrarDialogo, modifier = Modifier.testTag("dialogo_mas_tarde")) { Text("Más tarde") } }
            }
            is DialogoCaptura.YaRegistrada -> {
                { TextButton(onClick = acciones.onCerrarDialogo) { Text("Cerrar") } }
            }
            else -> null
        },
        modifier = Modifier.testTag("dialogo_captura"),
    )
}

@Preview(showBackground = true, widthDp = 390, heightDp = 900)
@Composable
private fun PaseListaScreenPreview() {
    SigehTheme {
        PaseListaScreen(
            estado = PaseListaUiState(
                cargando = false,
                servicio = "Medicina Interna",
                subtitulo = "Hospital A · America/Mexico_City",
                fecha = "2026-11-20",
                fechaTexto = "Viernes 20 nov 2026",
                turnos = listOf(ChipTurno("hor-tm", "TM"), ChipTurno("hor-tv", "TV")),
                filas = listOf(
                    FilaJornada("1", "Ana Rosales", "TM · 07:00–14:30", TipoFila.CAPTURADA, EstadoAsistencia.PRESENTE,
                        EstadoProgramacion.PROGRAMADA, "Capturado por Tú · 07:04 · validación pendiente", null, resaltada = true),
                    FilaJornada("2", "Jorge Salas", "TM · 07:00–14:30", TipoFila.CAPTURABLE, EstadoAsistencia.PENDIENTE,
                        EstadoProgramacion.PROGRAMADA, null, null, resaltada = false),
                    FilaJornada("3", "Lucía Navarro", "TM · 07:00–14:30", TipoFila.ENVIANDO, EstadoAsistencia.PENDIENTE,
                        EstadoProgramacion.PROGRAMADA, null, null, resaltada = false),
                    FilaJornada("6", "Daniela Cruz", "TM · 07:00–14:30", TipoFila.NO_CAPTURABLE, EstadoAsistencia.NO_EXIGIBLE,
                        EstadoProgramacion.CANCELADA, null, "No capturable: programación cancelada", resaltada = false),
                    FilaJornada("7", "Héctor Ruiz", "TV · 14:30–21:00", TipoFila.NO_CAPTURABLE, EstadoAsistencia.PENDIENTE,
                        EstadoProgramacion.PROGRAMADA, null, "Se habilita a las 14:30", resaltada = false),
                ),
                capturadas = 1,
                capturables = 3,
                conteos = mapOf(EstadoAsistencia.PRESENTE to 1, EstadoAsistencia.PENDIENTE to 2),
            ),
            acciones = AccionesPaseLista(),
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}
