package br.sono.diario.export

import br.sono.diario.data.DiaryEntry
import br.sono.diario.data.Medication
import br.sono.diario.data.MedicationIntake
import br.sono.diario.data.ScaleResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class PatientBundle(
    val format: Int = 1,
    val exportedAt: Long,
    val patientCode: String,
    val name: String,
    val birthYear: Int?,
    val sex: String,
    val diary: List<DiaryEntry>,
    val medications: List<Medication>,
    val intakes: List<MedicationIntake>,
    val scales: List<ScaleResult>,
)

class WrongPasswordException : Exception("Senha incorreta ou arquivo corrompido")

/**
 * Arquivo .sono: "SONO" + versão(1 byte) + sal(16) + iv(12) + JSON cifrado com AES-256-GCM.
 * A chave é derivada da senha com PBKDF2-HMAC-SHA256 (210.000 iterações, recomendação OWASP).
 */
object PatientExport {
    private val MAGIC = "SONO".toByteArray()
    private const val VERSION: Byte = 1
    private const val ITER = 210_000
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val k = f.generateSecret(PBEKeySpec(password, salt, ITER, 256)).encoded
        return SecretKeySpec(k, "AES")
    }

    fun encrypt(bundle: PatientBundle, password: CharArray): ByteArray {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also(rnd::nextBytes)
        val iv = ByteArray(12).also(rnd::nextBytes)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        c.updateAAD(MAGIC)
        val ct = c.doFinal(json.encodeToString(PatientBundle.serializer(), bundle).toByteArray())
        return ByteBuffer.allocate(MAGIC.size + 1 + 16 + 12 + ct.size)
            .put(MAGIC).put(VERSION).put(salt).put(iv).put(ct).array()
    }

    fun decrypt(data: ByteArray, password: CharArray): PatientBundle {
        require(data.size > 33 && data.copyOfRange(0, 4).contentEquals(MAGIC)) { "Arquivo não é um export do Diário do Sono" }
        val buf = ByteBuffer.wrap(data, 5, data.size - 5)
        val salt = ByteArray(16).also { buf.get(it) }
        val iv = ByteArray(12).also { buf.get(it) }
        val ct = ByteArray(buf.remaining()).also { buf.get(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        c.updateAAD(MAGIC)
        val plain = try { c.doFinal(ct) } catch (e: AEADBadTagException) { throw WrongPasswordException() }
        return json.decodeFromString(PatientBundle.serializer(), String(plain))
    }
}
