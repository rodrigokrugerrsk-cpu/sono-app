package br.sono.diario

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import androidx.room.Room
import br.sono.diario.data.AppDatabase
import br.sono.diario.data.DB_NAME
import br.sono.diario.data.buildDatabase
import androidx.sqlite.driver.AndroidSQLiteDriver
import java.io.File
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

fun androidDatabase(context: Context): AppDatabase {
    val app = context.applicationContext
    return buildDatabase(Room.databaseBuilder<AppDatabase>(app, app.getDatabasePath(DB_NAME).absolutePath), AndroidSQLiteDriver())
}

class AndroidStore(context: Context) : KeyValueStore {
    private val sp = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    override fun getString(key: String) = sp.getString(key, null)
    override fun putString(key: String, value: String) = sp.edit().putString(key, value).apply()
    override fun getInt(key: String, default: Int) = sp.getInt(key, default)
    override fun putInt(key: String, value: Int) = sp.edit().putInt(key, value).apply()
    override fun getBoolean(key: String, default: Boolean) = sp.getBoolean(key, default)
    override fun putBoolean(key: String, value: Boolean) = sp.edit().putBoolean(key, value).apply()
}

object JcaCrypto : CryptoProvider {
    private val rnd = SecureRandom()
    override fun randomBytes(n: Int) = ByteArray(n).also(rnd::nextBytes)
    override fun pbkdf2Sha256(password: String, salt: ByteArray, iterations: Int, keyBytes: Int): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password.toCharArray(), salt, iterations, keyBytes * 8)).encoded

    private fun cipher(mode: Int, key: ByteArray, iv: ByteArray, aad: ByteArray) =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv)); updateAAD(aad)
        }

    override fun aesGcmEncrypt(key: ByteArray, iv: ByteArray, aad: ByteArray, plain: ByteArray): ByteArray =
        cipher(Cipher.ENCRYPT_MODE, key, iv, aad).doFinal(plain)

    override fun aesGcmDecrypt(key: ByteArray, iv: ByteArray, aad: ByteArray, cipherAndTag: ByteArray): ByteArray? =
        try { cipher(Cipher.DECRYPT_MODE, key, iv, aad).doFinal(cipherAndTag) } catch (e: AEADBadTagException) { null }
}

/** Desenho do relatório sobre um Canvas do Android (PDF no aparelho, bitmap nos testes). */
class AndroidReportCanvas(private val c: Canvas) : ReportCanvas {
    private fun paint(size: Float, color: Int, bold: Boolean) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; this.color = color or 0xFF000000.toInt()
        typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }
    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color or 0xFF000000.toInt() }

    override fun text(s: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean) = c.drawText(s, x, y, paint(size, color, bold))
    override fun measure(s: String, size: Float, bold: Boolean) = paint(size, 0, bold).measureText(s)
    override fun rect(left: Float, top: Float, right: Float, bottom: Float, color: Int) = c.drawRect(left, top, right, bottom, fill(color))
    override fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, width: Float) =
        c.drawLine(x1, y1, x2, y2, fill(color).apply { strokeWidth = width })
    override fun triangle(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, color: Int) =
        c.drawPath(Path().apply { moveTo(x1, y1); lineTo(x2, y2); lineTo(x3, y3); close() }, fill(color))
}

class AndroidPdfDocument : ReportDocument {
    private val doc = PdfDocument()
    private var page: PdfDocument.Page? = null
    private var number = 0
    override fun newPage(width: Int, height: Int): ReportCanvas {
        val p = doc.startPage(PdfDocument.PageInfo.Builder(width, height, ++number).create())
        page = p
        return AndroidReportCanvas(p.canvas)
    }
    override fun finishPage() { page?.let { doc.finishPage(it) }; page = null }
    override fun save(path: String) { File(path).outputStream().use { doc.writeTo(it) }; doc.close() }
}

/** Parte da implementação Android que não depende da Activity. */
abstract class AndroidPlatformBase(protected val context: Context) : Platform {
    override val store: KeyValueStore by lazy { AndroidStore(context) }
    override val crypto: CryptoProvider = JcaCrypto

    override fun exportDir(): String = File(context.cacheDir, "exports").apply {
        mkdirs()
        // Remove exportações antigas (mais de 1 dia) para não deixar dados sensíveis no cache.
        listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000 }?.forEach { it.delete() }
    }.absolutePath

    override fun writeFile(path: String, bytes: ByteArray) = File(path).writeBytes(bytes)
    override fun newReportDocument(): ReportDocument = AndroidPdfDocument()

    override fun shareFile(path: String, mime: String, title: String) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", File(path))
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }
}
