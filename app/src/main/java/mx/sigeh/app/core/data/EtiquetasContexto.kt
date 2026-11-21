package mx.sigeh.app.core.data

import javax.inject.Inject

/**
 * Nombres visibles de hospital, división y servicio. `GET /auth/me` solo trae UUID y el backend
 * aún no publica un endpoint de nombres para el usuario (brecha 1 de `docs/mobile/contrato.md`, §7).
 * Cuando exista el propuesto `GET /auth/me/contextos`, solo cambia la implementación real.
 *
 * No se llama a `/hospitales`, `/servicios` ni `/hospitales/{id}/divisiones`: piden
 * `CATALOGOS_ADMINISTRAR`, una facultad que un jefe de servicio no tiene.
 */
interface EtiquetasContexto {
    suspend fun etiquetaHospital(id: String): String

    suspend fun etiquetaDivision(id: String): String

    suspend fun etiquetaServicio(id: String): String
}

/**
 * Modo real: un texto honesto con el final del identificador, por ejemplo «Servicio ··· c91e».
 * No inventa nombres.
 */
class EtiquetasPorIdentificador @Inject constructor() : EtiquetasContexto {
    override suspend fun etiquetaHospital(id: String): String = etiqueta("Hospital", id)

    override suspend fun etiquetaDivision(id: String): String = etiqueta("División", id)

    override suspend fun etiquetaServicio(id: String): String = etiqueta("Servicio", id)

    companion object {
        private const val DIGITOS = 4

        fun etiqueta(tipo: String, id: String): String = "$tipo ··· ${id.trim().takeLast(DIGITOS).lowercase()}"
    }
}
