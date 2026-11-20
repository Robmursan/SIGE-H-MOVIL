package mx.sigeh.app.core.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class SesionGuardada(val sesion: Session? = null)

/** Serializa la sesión como JSON y la cifra con Android Keystore antes de tocar el disco. */
internal class SesionCifradaSerializer(
    private val cipher: KeystoreCipher,
    private val json: Json,
) : Serializer<SesionGuardada> {

    override val defaultValue: SesionGuardada = SesionGuardada()

    override suspend fun readFrom(input: InputStream): SesionGuardada {
        val bytes = input.readBytes()
        if (bytes.isEmpty()) return defaultValue
        return try {
            json.decodeFromString(SesionGuardada.serializer(), cipher.descifrar(bytes).decodeToString())
        } catch (_: GeneralSecurityException) {
            // Llave perdida o datos alterados: se trata como "sin sesión" y se pide iniciar de nuevo.
            defaultValue
        } catch (_: SerializationException) {
            defaultValue
        } catch (_: IllegalArgumentException) {
            defaultValue
        }
    }

    override suspend fun writeTo(t: SesionGuardada, output: OutputStream) {
        output.write(cipher.cifrar(json.encodeToString(SesionGuardada.serializer(), t).encodeToByteArray()))
    }
}

@Singleton
class EncryptedSessionStore @Inject constructor(
    @ApplicationContext context: Context,
    json: Json,
) : SessionStore {

    private val dataStore: DataStore<SesionGuardada> = DataStoreFactory.create(
        serializer = SesionCifradaSerializer(KeystoreCipher(), json),
        produceFile = { context.dataStoreFile(ARCHIVO) },
    )

    @Volatile
    private var enMemoria: Session? = null

    override val session: Flow<Session?> = dataStore.data
        .map { it.sesion }
        .onEach { enMemoria = it }
        .distinctUntilChanged()

    override suspend fun actual(): Session? = session.first()

    /** Si aún no se ha leído el archivo cifrado, se lee una vez (solo desde hilos de red). */
    override fun sesionEnMemoria(): Session? = enMemoria ?: runBlocking { actual() }

    override suspend fun guardar(session: Session) {
        dataStore.updateData { SesionGuardada(session) }
        enMemoria = session
    }

    override suspend fun marcarVencida() {
        val actualizada = dataStore.updateData { guardada ->
            guardada.copy(sesion = guardada.sesion?.copy(vencida = true))
        }
        enMemoria = actualizada.sesion
    }

    override suspend fun limpiar() {
        dataStore.updateData { SesionGuardada() }
        enMemoria = null
    }

    private companion object {
        // Vive en files/datastore/, excluido de respaldos (res/xml).
        const val ARCHIVO = "sigeh_sesion.bin"
    }
}
