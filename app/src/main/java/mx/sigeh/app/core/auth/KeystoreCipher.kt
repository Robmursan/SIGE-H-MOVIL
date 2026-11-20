package mx.sigeh.app.core.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Cifrado AES-256-GCM con una llave que nunca sale de Android Keystore.
 * Sustituye a `EncryptedSharedPreferences` (androidx.security:security-crypto 1.1.0 marcó
 * sus APIs como obsoletas). Formato: [largo IV (1 byte)] [IV] [texto cifrado + etiqueta].
 */
class KeystoreCipher(
    private val alias: String = ALIAS_SESION,
) {
    fun cifrar(plano: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMACION)
        cipher.init(Cipher.ENCRYPT_MODE, llave())
        val iv = cipher.iv
        val cifrado = cipher.doFinal(plano)
        return byteArrayOf(iv.size.toByte()) + iv + cifrado
    }

    fun descifrar(datos: ByteArray): ByteArray {
        require(datos.isNotEmpty()) { "Sin datos" }
        val largoIv = datos[0].toInt()
        require(largoIv in 12..16 && datos.size > 1 + largoIv) { "Formato no válido" }
        val iv = datos.copyOfRange(1, 1 + largoIv)
        val cifrado = datos.copyOfRange(1 + largoIv, datos.size)
        val cipher = Cipher.getInstance(TRANSFORMACION)
        cipher.init(Cipher.DECRYPT_MODE, llave(), GCMParameterSpec(BITS_ETIQUETA, iv))
        return cipher.doFinal(cifrado)
    }

    private fun llave(): SecretKey {
        val keyStore = KeyStore.getInstance(PROVEEDOR).apply { load(null) }
        (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVEEDOR)
        generador.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generador.generateKey()
    }

    private companion object {
        const val ALIAS_SESION = "sigeh_sesion_v1"
        const val PROVEEDOR = "AndroidKeyStore"
        const val TRANSFORMACION = "AES/GCM/NoPadding"
        const val BITS_ETIQUETA = 128
    }
}
