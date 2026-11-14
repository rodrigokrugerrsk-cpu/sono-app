package br.sono.diario

import androidx.room.Room
import androidx.room.RoomDatabase
import br.sono.diario.data.AppDatabase
import br.sono.diario.data.DB_NAME
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSDateComponents
import platform.Foundation.NSFileManager
import platform.Foundation.NSMutableData
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication
import platform.UIKit.NSFontAttributeName
import platform.UIKit.NSForegroundColorAttributeName
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIBezierPath
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIGraphicsBeginPDFContextToData
import platform.UIKit.UIGraphicsBeginPDFPage
import platform.UIKit.UIGraphicsEndPDFContext
import platform.UIKit.UIRectFill
import platform.UIKit.drawAtPoint
import platform.UIKit.popoverPresentationController
import platform.UIKit.sizeWithAttributes
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Criptografia implementada em Swift (CryptoKit + CommonCrypto), porque o AES-GCM
 * não está disponível diretamente para Kotlin/Native. Ver iosApp/iosApp/AppleCrypto.swift.
 */
interface SwiftCrypto {
    fun randomBytes(count: Int): NSData
    fun pbkdf2Sha256(password: String, salt: NSData, iterations: Int, keyBytes: Int): NSData
    fun aesGcmSeal(key: NSData, iv: NSData, aad: NSData, plain: NSData): NSData
    fun aesGcmOpen(key: NSData, iv: NSData, aad: NSData, cipherAndTag: NSData): NSData?
}

private class SwiftCryptoAdapter(private val s: SwiftCrypto) : CryptoProvider {
    override fun randomBytes(n: Int) = s.randomBytes(n).toByteArray()
    override fun pbkdf2Sha256(password: String, salt: ByteArray, iterations: Int, keyBytes: Int) =
        s.pbkdf2Sha256(password, salt.toNSData(), iterations, keyBytes).toByteArray()
    override fun aesGcmEncrypt(key: ByteArray, iv: ByteArray, aad: ByteArray, plain: ByteArray) =
        s.aesGcmSeal(key.toNSData(), iv.toNSData(), aad.toNSData(), plain.toNSData()).toByteArray()
    override fun aesGcmDecrypt(key: ByteArray, iv: ByteArray, aad: ByteArray, cipherAndTag: ByteArray) =
        s.aesGcmOpen(key.toNSData(), iv.toNSData(), aad.toNSData(), cipherAndTag.toNSData())?.toByteArray()
}

private object IosStore : KeyValueStore {
    private val d = NSUserDefaults.standardUserDefaults
    override fun getString(key: String) = d.stringForKey(key)
    override fun putString(key: String, value: String) = d.setObject(value, forKey = key)
    override fun getInt(key: String, default: Int) = if (d.objectForKey(key) == null) default else d.integerForKey(key).toInt()
    override fun putInt(key: String, value: Int) = d.setInteger(value.toLong(), forKey = key)
    override fun getBoolean(key: String, default: Boolean) = if (d.objectForKey(key) == null) default else d.boolForKey(key)
    override fun putBoolean(key: String, value: Boolean) = d.setBool(value, forKey = key)
}

@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
private object IosReportCanvas : ReportCanvas {
    private fun font(size: Float, bold: Boolean) =
        if (bold) UIFont.boldSystemFontOfSize(size.toDouble()) else UIFont.systemFontOfSize(size.toDouble())
    private fun color(c: Int) = UIColor.colorWithRed(
        ((c shr 16) and 0xFF) / 255.0, green = ((c shr 8) and 0xFF) / 255.0, blue = (c and 0xFF) / 255.0, alpha = 1.0,
    )
    private fun attrs(size: Float, color: Int, bold: Boolean): Map<Any?, Any?> =
        mapOf(NSFontAttributeName to font(size, bold), NSForegroundColorAttributeName to color(color))

    @Suppress("CAST_NEVER_SUCCEEDS")
    private fun ns(s: String) = NSString.create(string = s)

    override fun text(s: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean) {
        // No iOS o ponto é o topo da linha; no relatório usamos a linha de base (como no Android).
        val top = y - font(size, bold).ascender
        ns(s).drawAtPoint(CGPointMake(x.toDouble(), top), withAttributes = attrs(size, color, bold))
    }

    override fun measure(s: String, size: Float, bold: Boolean): Float =
        ns(s).sizeWithAttributes(attrs(size, 0, bold)).useContents { width }.toFloat()

    override fun rect(left: Float, top: Float, right: Float, bottom: Float, color: Int) {
        color(color).setFill()
        UIRectFill(CGRectMake(left.toDouble(), top.toDouble(), (right - left).toDouble(), (bottom - top).toDouble()))
    }

    override fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, width: Float) {
        val p = UIBezierPath()
        p.moveToPoint(CGPointMake(x1.toDouble(), y1.toDouble()))
        p.addLineToPoint(CGPointMake(x2.toDouble(), y2.toDouble()))
        p.lineWidth = width.toDouble()
        color(color).setStroke()
        p.stroke()
    }

    override fun triangle(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, color: Int) {
        val p = UIBezierPath()
        p.moveToPoint(CGPointMake(x1.toDouble(), y1.toDouble()))
        p.addLineToPoint(CGPointMake(x2.toDouble(), y2.toDouble()))
        p.addLineToPoint(CGPointMake(x3.toDouble(), y3.toDouble()))
        p.closePath()
        color(color).setFill()
        p.fill()
    }
}

@OptIn(ExperimentalForeignApi::class)
private class IosPdfDocument : ReportDocument {
    private val data = NSMutableData()
    private var started = false
    override fun newPage(width: Int, height: Int): ReportCanvas {
        if (!started) {
            UIGraphicsBeginPDFContextToData(data, CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()), null)
            started = true
        }
        UIGraphicsBeginPDFPage()
        return IosReportCanvas
    }
    override fun finishPage() {}
    override fun save(path: String) {
        if (started) UIGraphicsEndPDFContext()
        data.writeToFile(path, atomically = true)
    }
}

/** Banco em Application Support, excluído do backup do iCloud. */
@OptIn(ExperimentalForeignApi::class)
fun iosDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val dir = NSFileManager.defaultManager.URLForDirectory(
        NSApplicationSupportDirectory, inDomain = NSUserDomainMask, appropriateForURL = null, create = true, error = null,
    )!!
    dir.setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)
    return Room.databaseBuilder<AppDatabase>(name = dir.path!! + "/" + DB_NAME)
}

@OptIn(ExperimentalForeignApi::class)
class IosPlatform(swiftCrypto: SwiftCrypto) : Platform {
    override val store: KeyValueStore = IosStore
    override val crypto: CryptoProvider = SwiftCryptoAdapter(swiftCrypto)

    override val biometricAvailable: Boolean
        get() = LAContext().canEvaluatePolicy(LAPolicyDeviceOwnerAuthentication, error = null)

    override fun authenticate(onResult: (Boolean) -> Unit) {
        LAContext().evaluatePolicy(
            LAPolicyDeviceOwnerAuthentication,
            localizedReason = "Confirme sua identidade para acessar seus dados de saúde",
        ) { ok, _ -> dispatch_async(dispatch_get_main_queue()) { onResult(ok) } }
    }

    override fun exportDir(): String {
        val dir = NSTemporaryDirectory() + "exports"
        NSFileManager.defaultManager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
        return dir
    }

    override fun writeFile(path: String, bytes: ByteArray) {
        bytes.toNSData().writeToFile(path, atomically = true)
    }

    override fun newReportDocument(): ReportDocument = IosPdfDocument()

    override fun shareFile(path: String, mime: String, title: String) {
        val top = topViewController() ?: return
        val vc = UIActivityViewController(activityItems = listOf(NSURL.fileURLWithPath(path)), applicationActivities = null)
        // No iPad a folha de compartilhamento precisa de um ponto de ancoragem.
        vc.popoverPresentationController?.sourceView = top.view
        top.view.bounds.useContents {
            vc.popoverPresentationController?.sourceRect = CGRectMake(size.width / 2, size.height / 2, 1.0, 1.0)
        }
        top.presentViewController(vc, animated = true, completion = null)
    }

    override fun requestNotificationPermission() {
        UNUserNotificationCenter.currentNotificationCenter()
            .requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) { _, _ -> }
    }

    override fun scheduleReminder(enabled: Boolean, minutes: Int) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.removePendingNotificationRequestsWithIdentifiers(listOf(REMINDER_ID))
        if (!enabled) return
        val content = UNMutableNotificationContent().apply {
            setTitle("Como foi sua noite?")
            setBody("Leva 1 minuto: preencha o diário do sono de hoje.")
            setSound(UNNotificationSound.defaultSound)
        }
        val time = NSDateComponents().apply { hour = (minutes / 60).toLong(); minute = (minutes % 60).toLong() }
        val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(time, repeats = true)
        center.addNotificationRequest(UNNotificationRequest.requestWithIdentifier(REMINDER_ID, content, trigger), withCompletionHandler = null)
    }

    private companion object { const val REMINDER_ID = "diary_reminder" }
}
