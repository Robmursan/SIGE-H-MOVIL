package mx.sigeh.app.core.data.fake

import mx.sigeh.app.core.common.fechaHoraLocal
import mx.sigeh.app.core.common.finDelDia
import mx.sigeh.app.core.data.ResultadoElemento
import mx.sigeh.app.core.database.entity.PendingCommandEntity
import mx.sigeh.app.core.database.entity.SyncReceiptEntity
import mx.sigeh.app.core.model.Agenda
import mx.sigeh.app.core.model.Alcance
import mx.sigeh.app.core.model.AsignacionOperativa
import mx.sigeh.app.core.model.AsistenciaPayload
import mx.sigeh.app.core.model.AvisoJornada
import mx.sigeh.app.core.model.CommandStatus
import mx.sigeh.app.core.model.CommandType
import mx.sigeh.app.core.model.ConvenioParaMi
import mx.sigeh.app.core.model.DiaSemana
import mx.sigeh.app.core.model.EstadoAsistencia
import mx.sigeh.app.core.model.EstadoDisponibilidadEventual
import mx.sigeh.app.core.model.EstadoMovimiento
import mx.sigeh.app.core.model.EstadoNecesidadCobertura
import mx.sigeh.app.core.model.EstadoPaseSalida
import mx.sigeh.app.core.model.Hospital
import mx.sigeh.app.core.model.IncidenciaPreliminarPayload
import mx.sigeh.app.core.model.JornadaProgramada
import mx.sigeh.app.core.model.Movimiento
import mx.sigeh.app.core.model.NecesidadCobertura
import mx.sigeh.app.core.model.PersonaEventual
import mx.sigeh.app.core.model.ProximoDia
import mx.sigeh.app.core.model.RegistroAsistenciaServidor
import mx.sigeh.app.core.model.RolAlcance
import mx.sigeh.app.core.model.Servicio
import mx.sigeh.app.core.model.SigehJson
import mx.sigeh.app.core.model.TipoDiaAgenda
import mx.sigeh.app.core.model.TipoIncidencia
import mx.sigeh.app.core.model.TipoMovimiento
import mx.sigeh.app.core.model.TonoAviso
import mx.sigeh.app.core.model.Turno
import mx.sigeh.app.core.model.Usuario

/**
 * Datos ficticios de M0 (prompt §6). Sábado 14 de noviembre, Hospital A, División de Medicina,
 * Medicina Interna, turno matutino 07:00–14:30, programación versión 3. Usuaria: M. Hernández.
 * Ningún dato corresponde a personas reales; los RUD son inventados y se muestran truncados.
 */
object DatosFicticios {

    val hospitalA = Hospital(id = "hosp-a", nombre = "Hospital A")

    val medicinaInterna = Servicio(
        id = "svc-mi",
        nombre = "Medicina Interna",
        divisionId = "div-med",
        divisionNombre = "División de Medicina",
        hospitalId = hospitalA.id,
    )

    val cirugiaGeneral = Servicio(
        id = "svc-cg",
        nombre = "Cirugía General",
        divisionId = "div-cir",
        divisionNombre = "División de Cirugía",
        hospitalId = hospitalA.id,
    )

    val matutino = Turno(id = "tur-mat", nombre = "Matutino", horarios = listOf("07:00 – 14:30"))

    val turnos = listOf(
        matutino,
        Turno(id = "tur-ves", nombre = "Vespertino", horarios = listOf("14:00 – 21:00")),
        Turno(id = "tur-noc", nombre = "Guardia nocturna", horarios = listOf("20:00 – 07:30")),
        Turno(id = "tur-ja", nombre = "Jornada acumulada", horarios = listOf("07:00 – 19:00", "09:00 – 21:00")),
    )

    val usuario = Usuario(id = "usr-0001", nombreCorto = "M. Hernández")

    fun alcances(ahoraMillis: Long): List<Alcance> = listOf(
        Alcance(
            id = "alc-mi",
            hospital = hospitalA,
            servicio = medicinaInterna,
            rol = RolAlcance.TITULAR,
            cargo = "Jefa de servicio",
            vigenteHastaMillis = null,
        ),
        Alcance(
            id = "alc-cg",
            hospital = hospitalA,
            servicio = cirugiaGeneral,
            rol = RolAlcance.ENCARGADO,
            cargo = "Encargada de turno",
            vigenteHastaMillis = finDelDia(ahoraMillis),
        ),
    )

    /** Encargo de turno anterior en Cirugía General que venció antes del envío (comando DENEGADO). */
    val serviciosConEncargoVencido = setOf(cirugiaGeneral.id)

    const val PASE_ID = "pase-mi-mat-0001"
    const val PROGRAMACION_VERSION = 3
    const val FECHA_CORTA = "Sáb 14 nov"

    private const val HORARIO = "07:00 – 14:30"

    val jornadas: List<JornadaProgramada> = listOf(
        JornadaProgramada("prog-0001", "emp-0001", "Ana Rosales M.", "Enf. general A", HORARIO, "4821", exigible = true),
        JornadaProgramada("prog-0002", "emp-0002", "Jorge Salas T.", "Enf. especialista B", HORARIO, "1937", exigible = true),
        JornadaProgramada(
            "prog-0003", "emp-0003", "Lucía Navarro P.", "Auxiliar de enfermería A", HORARIO, "6604", exigible = true,
            aviso = AvisoJornada("Funge como especialista en este servicio", TonoAviso.NEUTRO),
        ),
        JornadaProgramada(
            "prog-0004", "emp-0004", "Ricardo Ibarra G.", "Enf. general B", HORARIO, "2270", exigible = true,
            aviso = AvisoJornada("Convenio autorizado: cubre el turno de P. Durán", TonoAviso.INFORMATIVO),
        ),
        JornadaProgramada("prog-0005", "emp-0005", "Paola Gutiérrez L.", "Enf. general A", HORARIO, "5158", exigible = true),
        JornadaProgramada(
            "prog-0006", "emp-0006", "Daniela Cruz V.", "Auxiliar de enfermería B", HORARIO, "3349", exigible = true,
            aviso = AvisoJornada("Pase de salida autorizado a las 11:30", TonoAviso.ADVERTENCIA),
        ),
        JornadaProgramada(
            "prog-0007", "emp-0007", "Héctor Ruiz S.", "Enf. especialista A", HORARIO, "7712", exigible = true,
            aviso = AvisoJornada("Doble turno: revisar programación", TonoAviso.ADVERTENCIA),
        ),
        JornadaProgramada(
            "prog-0008", "emp-0008", "Marta Peña O.", "Enf. general B", HORARIO, "8846", exigible = false,
            motivoNoExigible = "Vacaciones autorizadas",
        ),
    )

    /** Lo que el servidor ya tiene: dos presentes aplicados y un registro validado por Supervisión. */
    fun registrosServidorIniciales(): Map<String, RegistroAsistenciaServidor> = mapOf(
        "prog-0001" to RegistroAsistenciaServidor("prog-0001", EstadoAsistencia.PRESENTE, validado = false),
        "prog-0002" to RegistroAsistenciaServidor("prog-0002", EstadoAsistencia.PRESENTE, validado = false),
        "prog-0005" to RegistroAsistenciaServidor(
            "prog-0005", EstadoAsistencia.PRESENTE, validado = true,
            validadoPor = "la supervisora L. Ortega", horaValidacion = "07:20",
        ),
        "prog-0008" to RegistroAsistenciaServidor("prog-0008", EstadoAsistencia.NO_EXIGIBLE, validado = false),
    )

    val tiposIncidencia: List<TipoIncidencia> = listOf(
        TipoIncidencia("inc-fpj", "Falta pendiente de justificar", "Ausencia", permiteParcial = false),
        TipoIncidencia("inc-ret", "Retardo", "Ausencia parcial", permiteParcial = true),
        TipoIncidencia("inc-lcg", "Licencia con goce", "Licencias", permiteParcial = true),
        TipoIncidencia("inc-lsg", "Licencia sin goce", "Licencias", permiteParcial = true),
        TipoIncidencia("inc-inc", "Incapacidad", "Salud laboral · sin datos clínicos", permiteParcial = false),
        TipoIncidencia("inc-ono", "Onomástico", "Descansos y permisos", permiteParcial = false),
    )

    val movimientosServicio: List<Movimiento> = listOf(
        Movimiento(
            "mov-0001", TipoMovimiento.CONVENIO, "R. Ibarra cubre a P. Durán", "Hoy · Matutino 07:00 – 14:30",
            estado = EstadoMovimiento.ACEPTADO_POR_SUSTITUTO,
        ),
        Movimiento(
            "mov-0002", TipoMovimiento.PASE_SALIDA, "D. Cruz · salida 11:30",
            "Hoy · regreso sin registrar (no es obligatorio)", estadoPase = EstadoPaseSalida.AUTORIZADO,
        ),
        Movimiento(
            "mov-0003", TipoMovimiento.CONVENIO, "L. Navarro cubrirá a J. Salas", "Lun 16 nov (festivo) · Matutino",
            estado = EstadoMovimiento.SOLICITADO,
        ),
        Movimiento(
            "mov-0004", TipoMovimiento.CAMBIO_DESCANSO, "H. Ruiz · jue 19 → vie 20", "Solicitado por el trabajador",
            estado = EstadoMovimiento.SOLICITADO,
        ),
    )

    val conveniosParaMi: List<ConvenioParaMi> = listOf(
        ConvenioParaMi(
            movimientoId = "mov-0005",
            solicitante = "A. Rosales",
            fecha = "Domingo 22 nov",
            turno = "Matutino 07:00 – 14:30",
            servicio = "Medicina Interna",
            tipoConvenio = "Sin intercambio posterior",
            estadoJefatura = EstadoMovimiento.AUTORIZADO,
        ),
    )

    const val ASIGNACION_ID = "asig-0001"

    val necesidades: List<NecesidadCobertura> = listOf(
        NecesidadCobertura(
            id = "nec-0001",
            titulo = "Plaza vacante · enf. general",
            detalle = "Coordinación busca personal eventual",
            horario = HORARIO,
            estado = EstadoNecesidadCobertura.EN_ASIGNACION,
            asignacionPendienteId = ASIGNACION_ID,
        ),
        NecesidadCobertura(
            id = "nec-0002",
            titulo = "Convenio · R. Ibarra por P. Durán",
            detalle = "Autorizado por la jefatura",
            horario = HORARIO,
            estado = EstadoNecesidadCobertura.CUBIERTA,
        ),
    )

    val asignacionSofia = AsignacionOperativa(
        id = ASIGNACION_ID,
        necesidad = necesidades.first(),
        persona = PersonaEventual(
            nombre = "Sofía Medina R.",
            iniciales = "SM",
            categoria = "Enf. general A",
            rudUltimos4 = "9083",
            competenciasVerificadas = listOf("Catéter venoso central", "Hemoderivados"),
        ),
        llegadaCoordinacion = "06:58",
        asignadaA = "07:42",
        estadoEventual = EstadoDisponibilidadEventual.ASIGNADA,
    )

    /** Jornada propia de M. Hernández, para la copia local de la programación. */
    const val PROGRAMACION_PROPIA_ID = "prog-9001"
    val copiaGuardadaMillis: Long = fechaHoraLocal(2026, 11, 14, 6, 52)
    val inicioTurnoMillis: Long = fechaHoraLocal(2026, 11, 14, 7, 0)
    val finTurnoMillis: Long = fechaHoraLocal(2026, 11, 14, 14, 30)

    fun agenda(copiaGuardada: Long?): Agenda = Agenda(
        hoyEtiqueta = "HOY · SÁBADO 14 DE NOVIEMBRE",
        horario = HORARIO,
        turnoServicio = "Turno matutino · Medicina Interna",
        programacionVersion = PROGRAMACION_VERSION,
        descansoSemanal = "martes y miércoles",
        semanaTitulo = "Semana del 9 al 15 de noviembre",
        semana = listOf(
            DiaSemana("L", 9, "M", TipoDiaAgenda.LABORAL, esHoy = false),
            DiaSemana("M", 10, "Desc", TipoDiaAgenda.DESCANSO, esHoy = false),
            DiaSemana("Mi", 11, "Desc", TipoDiaAgenda.DESCANSO, esHoy = false),
            DiaSemana("J", 12, "M", TipoDiaAgenda.LABORAL, esHoy = false),
            DiaSemana("V", 13, "M", TipoDiaAgenda.LABORAL, esHoy = false),
            DiaSemana("S", 14, "M", TipoDiaAgenda.LABORAL, esHoy = true),
            DiaSemana("D", 15, "M", TipoDiaAgenda.LABORAL, esHoy = false),
        ),
        leyenda = "M = matutino · Desc = descanso programado",
        proximos = listOf(
            ProximoDia("Domingo 15 nov", "Matutino · 07:00 – 14:30", "Programado", TipoDiaAgenda.LABORAL),
            ProximoDia("Lunes 16 nov", "Festivo oficial · turno habitual", "Festivo", TipoDiaAgenda.FESTIVO),
            ProximoDia("Martes 17 nov", "Descanso semanal", "Descanso", TipoDiaAgenda.DESCANSO),
            ProximoDia("Miércoles 18 nov", "Descanso semanal", "Descanso", TipoDiaAgenda.DESCANSO),
        ),
        copiaGuardadaMillis = copiaGuardada,
    )

    // ---- Cola local de ejemplo (prompt §6, pantalla 10) -------------------------------------

    const val CMD_ANA = "6f1c2a3b-0001-4a5b-8c6d-000000000001"
    const val CMD_JORGE = "6f1c2a3b-0002-4a5b-8c6d-000000000002"
    const val CMD_RICARDO = "6f1c2a3b-0004-4a5b-8c6d-000000000004"
    const val CMD_PAOLA = "6f1c2a3b-0005-4a5b-8c6d-000000000005"
    const val CMD_INCIDENCIA_PAOLA = "6f1c2a3b-0105-4a5b-8c6d-000000000105"
    const val CMD_LOPEZ = "6f1c2a3b-0201-4a5b-8c6d-000000000201"

    private fun hora(h: Int, m: Int) = fechaHoraLocal(2026, 11, 14, h, m)

    private fun asistencia(programacionId: String, estado: EstadoAsistencia, etiqueta: String) =
        SigehJson.encodeToString(AsistenciaPayload.serializer(), AsistenciaPayload(programacionId, estado, etiqueta))

    fun comandosIniciales(): List<PendingCommandEntity> {
        fun comando(
            id: String,
            programacionId: String,
            tipo: CommandType,
            payload: String,
            creado: Long,
            estado: CommandStatus,
            intentos: Int,
            error: String? = null,
            servicioId: String = medicinaInterna.id,
        ) = PendingCommandEntity(
            clientEventId = id,
            actorId = usuario.id,
            hospitalId = hospitalA.id,
            serviceId = servicioId,
            programacionId = programacionId,
            type = tipo,
            payloadJson = payload,
            expectedVersion = PROGRAMACION_VERSION,
            createdAtDevice = creado,
            status = estado,
            attemptCount = intentos,
            lastErrorCode = error,
        )
        return listOf(
            comando(CMD_ANA, "prog-0001", CommandType.ASISTENCIA,
                asistencia("prog-0001", EstadoAsistencia.PRESENTE, "A. Rosales"), hora(7, 1), CommandStatus.APLICADO, 1),
            comando(CMD_JORGE, "prog-0002", CommandType.ASISTENCIA,
                asistencia("prog-0002", EstadoAsistencia.PRESENTE, "J. Salas"), hora(7, 2), CommandStatus.YA_APLICADO, 2),
            comando(CMD_PAOLA, "prog-0005", CommandType.ASISTENCIA,
                asistencia("prog-0005", EstadoAsistencia.AUSENTE, "P. Gutiérrez"), hora(7, 12), CommandStatus.CONFLICTO, 1,
                error = "REGISTRO_VALIDADO"),
            comando(CMD_RICARDO, "prog-0004", CommandType.ASISTENCIA,
                asistencia("prog-0004", EstadoAsistencia.PRESENTE, "R. Ibarra"), hora(7, 14), CommandStatus.PENDIENTE_LOCAL, 0),
            comando(
                CMD_INCIDENCIA_PAOLA, "prog-0005", CommandType.INCIDENCIA_PRELIMINAR,
                SigehJson.encodeToString(
                    IncidenciaPreliminarPayload.serializer(),
                    IncidenciaPreliminarPayload(
                        programacionId = "prog-0005",
                        tipoIncidenciaId = "inc-ret",
                        tipoNombre = "Retardo",
                        jornadaCompleta = false,
                        desde = "07:00",
                        hasta = "08:15",
                        etiqueta = "P. Gutiérrez",
                    ),
                ),
                hora(7, 21), CommandStatus.PENDIENTE_LOCAL, 0,
            ),
            comando(CMD_LOPEZ, "prog-0101", CommandType.ASISTENCIA,
                asistencia("prog-0101", EstadoAsistencia.PRESENTE, "M. López"), hora(6, 40), CommandStatus.DENEGADO, 1,
                error = "ALCANCE_VENCIDO", servicioId = cirugiaGeneral.id),
        )
    }

    fun recibosIniciales(): List<SyncReceiptEntity> = listOf(
        SyncReceiptEntity(CMD_ANA, "srv-evt-0001", hora(7, 3), 201),
        SyncReceiptEntity(CMD_JORGE, "srv-evt-0002", hora(7, 5), 200),
    )

    /** Respuestas que el servidor ficticio ya tiene guardadas (inbox de idempotencia). */
    fun inboxInicial(): List<ResultadoElemento> = listOf(
        ResultadoElemento(CMD_ANA, CommandStatus.APLICADO, 201, "srv-evt-0001", hora(7, 3)),
        ResultadoElemento(CMD_JORGE, CommandStatus.APLICADO, 201, "srv-evt-0002", hora(7, 4)),
        ResultadoElemento(CMD_PAOLA, CommandStatus.CONFLICTO, 409, codigo = "REGISTRO_VALIDADO"),
        ResultadoElemento(CMD_LOPEZ, CommandStatus.DENEGADO, 403, codigo = "ALCANCE_VENCIDO"),
    )
}
