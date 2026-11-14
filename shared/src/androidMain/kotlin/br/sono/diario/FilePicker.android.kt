package br.sono.diario

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberFilePicker(onResult: (ByteArray?) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        onResult(uri?.let { u -> runCatching { ctx.contentResolver.openInputStream(u)!!.use { it.readBytes() } }.getOrNull() })
    }
    return { launcher.launch(arrayOf("*/*")) }
}
