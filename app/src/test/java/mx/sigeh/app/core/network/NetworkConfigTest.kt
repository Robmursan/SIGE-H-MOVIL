package mx.sigeh.app.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkConfigTest {

    private val url = "http://10.0.2.2:3000/api/v1"

    @Test
    fun asistenciaReal_exigeIamRealYUrlBase() {
        assertTrue(NetworkConfig(url, iamRealSolicitado = true, asistenciaRealSolicitada = true).asistenciaReal)
        assertFalse("Sin IAM real no hay tokens", NetworkConfig(url, iamRealSolicitado = false, asistenciaRealSolicitada = true).asistenciaReal)
        assertFalse("Sin URL base todo es Fake", NetworkConfig("", iamRealSolicitado = true, asistenciaRealSolicitada = true).asistenciaReal)
    }

    @Test
    fun iamRealConAsistenciaFake_estaPermitido() {
        val config = NetworkConfig(url, iamRealSolicitado = true, asistenciaRealSolicitada = false)

        assertTrue(config.iamReal)
        assertFalse(config.asistenciaReal)
    }

    @Test
    fun urlBase_terminaEnDiagonal() {
        assertEquals("$url/", NetworkConfig(url, iamRealSolicitado = true).baseUrl)
    }
}
