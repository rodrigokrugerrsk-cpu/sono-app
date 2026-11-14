package br.sono.diario.export

import br.sono.diario.CryptoProvider
import br.sono.diario.data.DiaryEntry
import br.sono.diario.data.Medication
import br.sono.diario.data.MedicationIntake
import br.sono.diario.data.ScaleResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
 * O formato é idêntico no Android e no iPhone.
 */
object PatientExport {
    private val MAGIC = "SONO".encodeToByteArray()
    private const val VERSION: Byte = 1
    private const val ITER = 210_000
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encrypt(crypto: CryptoProvider, bundle: PatientBundle, password: String): ByteArray {
        val salt = crypto.randomBytes(16)
        val iv = crypto.randomBytes(12)
        val key = crypto.pbkdf2Sha256(password, salt, ITER, 32)
        val ct = crypto.aesGcmEncrypt(key, iv, MAGIC, json.encodeToString(PatientBundle.serializer(), bundle).encodeToByteArray())
        return MAGIC + byteArrayOf(VERSION) + salt + iv + ct
    }

    fun decrypt(crypto: CryptoProvider, data: ByteArray, password: String): PatientBundle {
        require(data.size > 33 + 16 && data.copyOfRange(0, 4).contentEquals(MAGIC)) { "Arquivo não é um export do Diário do Sono" }
        val salt = data.copyOfRange(5, 21)
        val iv = data.copyOfRange(21, 33)
        val ct = data.copyOfRange(33, data.size)
        val key = crypto.pbkdf2Sha256(password, salt, ITER, 32)
        val plain = crypto.aesGcmDecrypt(key, iv, MAGIC, ct) ?: throw WrongPasswordException()
        return json.decodeFromString(PatientBundle.serializer(), plain.decodeToString())
    }
}
