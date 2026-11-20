package mx.sigeh.app.core.data.fake

import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mx.sigeh.app.core.auth.FacultadesAsistencia
import mx.sigeh.app.core.auth.PoliticaAmbito
import mx.sigeh.app.core.auth.SessionStore
import mx.sigeh.app.core.common.FormatoHora
import mx.sigeh.app.core.common.Reloj
import mx.sigeh.app.core.data.CachePaseLista
import mx.sigeh.app.core.data.EtiquetasContexto
import mx.sigeh.app.core.data.EtiquetasPorIdentificador
import mx.sigeh.app.core.data.MotivoRechazo
import mx.sigeh.app.core.data.PaseListaRepository
import mx.sigeh.app.core.data.ResultadoCaptura
import mx.sigeh.app.core.data.ResultadoConsulta
import mx.sigeh.app.core.data.ResultadoRevocacion
import mx.sigeh.app.core.data.ResultadoSesiones
import mx.sigeh.app.core.data.SesionIam
import mx.sigeh.app.core.data.SesionesRepository
import mx.sigeh.app.core.model.AcuseCaptura
import mx.sigeh.app.core.model.EstadoCaptura
import mx.sigeh.app.core.model.JornadaPase
import mx.sigeh.app.core.model.MotivoNoCapturable
import mx.sigeh.app.core.model.PaginaPaseLista
import mx.sigeh.app.core.model.UltimaCaptura
import mx.sigeh.app.core.model.ValidacionAdministrativa

/*
 * Fakes de M2 con el mismo modelo y las mismas reglas que el backend (prompt M2 §10). Son los que
 * se usan en la demostración si el backend no está disponible.
 */

/** Latencia simulada de 400–800 ms, para que «Enviando…» sea visible. */
private suspend fun latencia() = delay(Random.nextLong(400, 801))

/**
 * Pase de lista Fake. Imita al servidor:
 * - la misma `Idempotency-Key` con la misma jornada y estado devuelve **el mismo** acuse;
 * - la misma clave con otro estado (u otra jornada) da `IDEMPOTENCY_CONFLICT`;
 * - una clave nueva sobre una jornada ya capturada da `YA_REGISTRADA`;
 * - sin `ASISTENCIA_CAPTURAR` en el servicio las jornadas salen con `SIN_FACULTAD_CAPTURA` y el
 *   `PUT` responde como un 403.
 *
 * Sirve para cualquier servicio, también con IAM real y `ASISTENCIA_ENABLED=false` en el backend.
 */
@Singleton
class FakePaseListaRepository @Inject constructor(
    private val sessionStore: SessionStore,
    private val reloj: Reloj,
) : PaseListaRepository {

    private data class Intencion(val programacionId: String, val estado: EstadoCaptura, val acuse: AcuseCaptura)

    private val mutex = Mutex()
    private val cache = CachePaseLista()

    /** «Servidor»: jornadas por servicio y fecha, con las capturas hechas en esta ejecución. */
    private val dias = mutableMapOf<String, LinkedHashMap<String, JornadaPase>>()

    /** Inbox de idempotencia: clave → intención ya aplicada. */
    private val claves = mutableMapOf<String, Intencion>()
    private var siguienteEvento = BigInteger(PRIMER_EVENTO)

    override suspend fun consultar(
        hospitalId: String,
        servicioId: String,
        fecha: String,
        horarioId: String?,
        cursor: String?,
    ): ResultadoConsulta {
        latencia()
        val facultades = facultadesEn(servicioId)
        if (!facultades.consultarPase) return ResultadoConsulta.SinPermiso
        val jornadas = mutex.withLock { dia(servicioId, fecha).values.toList() }
            .filter { horarioId.isNullOrBlank() || it.horarioId == horarioId }
            .map { if (facultades.capturar) it else it.soloConsulta() }
        // El Fake cabe en una sola página (limit 100): nunca hay cursor siguiente.
        val pagina = PaginaPaseLista(
            hospitalId = hospitalId,
            servicioId = servicioId,
            fecha = fecha,
            zonaHoraria = DatosFicticios.ZONA_HOSPITAL,
            jornadas = if (cursor.isNullOrBlank()) jornadas else emptyList(),
            siguienteCursor = null,
        )
        cache.registrarPagina(pagina, primeraPagina = cursor.isNullOrBlank())
        return ResultadoConsulta.Exito(pagina)
    }

    override suspend fun capturar(
        programacionId: String,
        estado: EstadoCaptura,
        idempotencyKey: String,
    ): ResultadoCaptura {
        latencia()
        return mutex.withLock {
            claves[idempotencyKey]?.let { previa ->
                return@withLock if (previa.programacionId == programacionId && previa.estado == estado) {
                    ResultadoCaptura.Confirmada(previa.acuse)
                } else {
                    ResultadoCaptura.Rechazada(MotivoRechazo.CONFLICTO_IDEMPOTENCIA)
                }
            }
            val (llave, jornada) = buscar(programacionId) ?: return@withLock ResultadoCaptura.NoEncontrada
            if (!facultadesEn(servicioDe(llave)).capturar) return@withLock ResultadoCaptura.SinPermiso
            if (jornada.capturada) return@withLock ResultadoCaptura.Rechazada(MotivoRechazo.YA_REGISTRADA)
            if (!jornada.capturable) return@withLock rechazoPor(jornada.motivoNoCapturable)

            val acuse = AcuseCaptura(
                programacionId = programacionId,
                asistenciaId = "asi-$programacionId",
                eventoId = siguienteEvento.toString().also { siguienteEvento = siguienteEvento.add(BigInteger.ONE) },
                estado = estado,
                validacion = ValidacionAdministrativa.PENDIENTE,
                actorId = actorActual(),
                registradoEn = FormatoHora.iso(reloj.ahoraMillis(), DatosFicticios.ZONA_HOSPITAL),
                revision = "1",
            )
            dias.getValue(llave)[programacionId] = jornada.copy(
                capturada = true,
                asistenciaId = acuse.asistenciaId,
                estadoOperativo = estado.aEstadoAsistencia(),
                validacion = acuse.validacion,
                revision = acuse.revision,
                ultimaCaptura = UltimaCaptura(acuse.eventoId, acuse.actorId, acuse.registradoEn),
                capturable = false,
                motivoNoCapturable = MotivoNoCapturable.YA_REGISTRADA,
            )
            claves[idempotencyKey] = Intencion(programacionId, estado, acuse)
            cache.registrarAcuse(acuse)
            ResultadoCaptura.Confirmada(acuse)
        }
    }

    override fun jornada(programacionId: String): JornadaPase? = cache.jornada(programacionId)

    override fun acuse(programacionId: String): AcuseCaptura? = cache.acuse(programacionId)

    /** Solo la caché de la app; el «servidor» Fake conserva sus registros como lo haría el real. */
    override fun limpiar() = cache.limpiar()

    private fun dia(servicioId: String, fecha: String): LinkedHashMap<String, JornadaPase> =
        dias.getOrPut(llaveDia(servicioId, fecha)) {
            DatosFicticios.jornadasDelDia(prefijo = "m2-$servicioId-$fecha", fecha = fecha, actorPropioId = actorActual())
                .associateByTo(LinkedHashMap()) { it.programacionId }
        }

    private fun buscar(programacionId: String): Pair<String, JornadaPase>? =
        dias.firstNotNullOfOrNull { (llave, jornadas) -> jornadas[programacionId]?.let { llave to it } }

    /** Sesión guardada por M0 (sin asignaciones): se le da acceso completo, como antes. */
    private fun facultadesEn(servicioId: String): FacultadesAsistencia {
        val asignaciones = sessionStore.sesionEnMemoria()?.asignaciones ?: return FacultadesAsistencia(true, true, true, true)
        return PoliticaAmbito.facultadesEn(servicioId, asignaciones)
    }

    private fun actorActual(): String = sessionStore.sesionEnMemoria()?.usuarioId ?: DatosFicticios.usuario.id

    private companion object {
        const val PRIMER_EVENTO = "9007199254741000"
        const val SEPARADOR = "|"

        fun llaveDia(servicioId: String, fecha: String) = "$servicioId$SEPARADOR$fecha"

        fun servicioDe(llave: String) = llave.substringBefore(SEPARADOR)

        /** Mismo orden de motivos que el backend: SIN_FACULTAD_CAPTURA solo si no hay otro. */
        fun JornadaPase.soloConsulta(): JornadaPase =
            if (capturable) copy(capturable = false, motivoNoCapturable = MotivoNoCapturable.SIN_FACULTAD_CAPTURA) else this

        fun rechazoPor(motivo: MotivoNoCapturable?): ResultadoCaptura = when (motivo) {
            MotivoNoCapturable.YA_REGISTRADA -> ResultadoCaptura.Rechazada(MotivoRechazo.YA_REGISTRADA)
            MotivoNoCapturable.FUERA_VENTANA -> ResultadoCaptura.Rechazada(MotivoRechazo.FUERA_VENTANA)
            MotivoNoCapturable.PROGRAMACION_NO_CAPTURABLE -> ResultadoCaptura.Rechazada(MotivoRechazo.PROGRAMACION_NO_CAPTURABLE)
            MotivoNoCapturable.CONTRATO_NO_VIGENTE -> ResultadoCaptura.Rechazada(MotivoRechazo.CONTRATO_NO_VIGENTE)
            MotivoNoCapturable.CIERRE_EXISTENTE -> ResultadoCaptura.Rechazada(MotivoRechazo.CIERRE_EXISTENTE)
            // El backend responde 403 «Acceso denegado» en este caso.
            MotivoNoCapturable.SIN_FACULTAD_CAPTURA -> ResultadoCaptura.SinPermiso
            null -> ResultadoCaptura.Rechazada(MotivoRechazo.NO_ADMITE_CAPTURA)
        }
    }
}

/** Sesiones Fake para la pantalla de Cuenta. Revocar marca la sesión, no la borra (como el servidor). */
@Singleton
class FakeSesionesRepository @Inject constructor(
    private val reloj: Reloj,
) : SesionesRepository {

    private val mutex = Mutex()
    private var sesiones: List<SesionIam> = DatosFicticios.sesionesIam()

    override suspend fun sesiones(): ResultadoSesiones {
        latencia()
        return ResultadoSesiones.Exito(mutex.withLock { sesiones })
    }

    override suspend fun revocar(sessionId: String): ResultadoRevocacion {
        latencia()
        return mutex.withLock {
            val sesion = sesiones.firstOrNull { it.sessionId == sessionId } ?: return@withLock ResultadoRevocacion.NoEncontrada
            if (!sesion.revocada) {
                val ahora = FormatoHora.iso(reloj.ahoraMillis(), "UTC")
                sesiones = sesiones.map { if (it.sessionId == sessionId) it.copy(revocadaEn = ahora) else it }
            }
            ResultadoRevocacion.Revocada(eraLaActual = sesion.actual)
        }
    }
}

/** Nombres de [DatosFicticios] para sus identificadores; para cualquier otro, el texto del modo real. */
class FakeEtiquetasContexto @Inject constructor() : EtiquetasContexto {
    override suspend fun etiquetaHospital(id: String): String =
        DatosFicticios.nombresContexto[id] ?: EtiquetasPorIdentificador.etiqueta("Hospital", id)

    override suspend fun etiquetaDivision(id: String): String =
        DatosFicticios.nombresContexto[id] ?: EtiquetasPorIdentificador.etiqueta("División", id)

    override suspend fun etiquetaServicio(id: String): String =
        DatosFicticios.nombresContexto[id] ?: EtiquetasPorIdentificador.etiqueta("Servicio", id)
}
