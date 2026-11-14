package br.sono.diario.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.sono.diario.resources.Res
import br.sono.diario.resources.great_vibes

val BrandPink = Color(0xFFF8C8DC)
val BrandPlum = Color(0xFF2E1A3A)
val BrandScript: FontFamily
    @Composable get() = FontFamily(Font(Res.font.great_vibes))

/** Logotipo "Sono by Dra Alessandra" em rosa claro sobre fundo ameixa. */
@Composable
fun BrandLogo(modifier: Modifier = Modifier, scale: Float = 1f) {
    Column(
        modifier.clip(RoundedCornerShape(24.dp)).background(BrandPlum).padding(horizontal = (28 * scale).dp, vertical = (14 * scale).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Sono", fontFamily = BrandScript, fontSize = (56 * scale).sp, color = BrandPink)
        Text("by Dra Alessandra", fontFamily = BrandScript, fontSize = (24 * scale).sp, color = BrandPink.copy(alpha = 0.85f))
    }
}
