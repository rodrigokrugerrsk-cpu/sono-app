package br.sono.diario

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypeData
import platform.darwin.NSObject

@Composable
actual fun rememberFilePicker(onResult: (ByteArray?) -> Unit): () -> Unit {
    val callback by rememberUpdatedState(onResult)
    val delegate = remember {
        object : NSObject(), UIDocumentPickerDelegateProtocol {
            override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
                val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
                val scoped = url?.startAccessingSecurityScopedResource() ?: false
                val data = url?.let { NSData.dataWithContentsOfURL(it) }
                if (scoped) url.stopAccessingSecurityScopedResource()
                callback(data?.toByteArray())
            }

            override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) = callback(null)
        }
    }
    return {
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeData), asCopy = true)
        picker.delegate = delegate
        topViewController()?.presentViewController(picker, animated = true, completion = null)
    }
}
