package br.sono.diario

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/** Armazenamento simples de preferências (SharedPreferences no Android, NSUserDefaults no iOS). */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun getInt(key: String, default: Int): Int
    fun putInt(key: String, value: Int)
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
}

/** Primitivas criptográficas. O formato do arquivo .sono é o mesmo nas duas plataformas. */
interface CryptoProvider {
    fun randomBytes(n: Int): ByteArray
    fun pbkdf2Sha256(password: String, salt: ByteArray, iterations: Int, keyBytes: Int): ByteArray
    /** Retorna texto cifrado seguido da tag de 16 bytes (mesmo layout do Java e do CryptoKit). */
    fun aesGcmEncrypt(key: ByteArray, iv: ByteArray, aad: ByteArray, plain: ByteArray): ByteArray
    /** Retorna null se a senha estiver errada ou o arquivo tiver sido alterado. */
    fun aesGcmDecrypt(key: ByteArray, iv: ByteArray, aad: ByteArray, cipherAndTag: ByteArray): ByteArray?
}

/** Superfície de desenho de uma página do relatório, em pontos (A4 = 595 x 842). */
interface ReportCanvas {
    /** Desenha texto com a linha de base em [y]. Cor no formato 0xRRGGBB. */
    fun text(s: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean = false)
    fun measure(s: String, size: Float, bold: Boolean = false): Float
    fun rect(left: Float, top: Float, right: Float, bottom: Float, color: Int)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, width: Float)
    fun triangle(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, color: Int)
}

interface ReportDocument {
    fun newPage(width: Int, height: Int): ReportCanvas
    fun finishPage()
    fun save(path: String)
}

/** Serviços que cada plataforma implementa. */
interface Platform {
    val store: KeyValueStore
    val crypto: CryptoProvider
    val biometricAvailable: Boolean

    /** Pasta temporária para arquivos exportados. */
    fun exportDir(): String
    fun writeFile(path: String, bytes: ByteArray)
    fun newReportDocument(): ReportDocument
    fun shareFile(path: String, mime: String, title: String)
    fun scheduleReminder(enabled: Boolean, minutes: Int)
    fun requestNotificationPermission()
    fun authenticate(onResult: (Boolean) -> Unit)
}

val LocalPlatform = staticCompositionLocalOf<Platform> { error("Platform não fornecida") }

/** Abre o seletor de arquivos do sistema e devolve o conteúdo do arquivo escolhido (ou null). */
@Composable
expect fun rememberFilePicker(onResult: (ByteArray?) -> Unit): () -> Unit
