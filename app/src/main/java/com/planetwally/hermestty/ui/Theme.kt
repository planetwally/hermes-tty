package com.planetwally.hermestty.ui

import com.planetwally.hermestty.R
import com.planetwally.hermestty.Tone
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp

/** Hermes CLI "default" skin (gold & kawaii), dark variant — same values as hermes_cli/skin_engine.py. */
object Hx {
    val bg = Color(0xFF0E0E1A)
    val panel = Color(0xFF1A1A2E)        // status_bar_bg / completion_menu_bg
    val panelHi = Color(0xFF333355)      // completion_menu_current_bg
    val selection = Color(0xFF3A3A55)
    val border = Color(0xFFCD7F32)       // banner_border / input_rule (bronze)
    val title = Color(0xFFFFD700)        // banner_title / response_border (gold)
    val accent = Color(0xFFFFBF00)       // banner_accent / ui_accent (amber)
    val dim = Color(0xFFB8860B)          // banner_dim
    val text = Color(0xFFFFF8DC)         // banner_text / prompt (cornsilk)
    val label = Color(0xFFDAA520)        // ui_label / session_label
    val ok = Color(0xFF4CAF50)
    val error = Color(0xFFEF5350)
    val warn = Color(0xFFFFA726)
    val statusText = Color(0xFFC0C0C0)
    val statusDim = Color(0xFF8A7A4A)
    val good = Color(0xFF8FBC8F)
    val bad = Color(0xFFFF8C00)
    val muted = Color(0xFF8B8682)        // session_border
    val shell = Color(0xFF4DABF7)        // shell_dollar
}

val Mono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

val LocalTermSize = staticCompositionLocalOf { 13 }

fun Tone.color() = when (this) {
    Tone.DIM -> Hx.muted
    Tone.INFO -> Hx.label
    Tone.OK -> Hx.ok
    Tone.WARN -> Hx.warn
    Tone.ERROR -> Hx.error
}

@Composable
fun termStyle(color: Color = Hx.text, scale: Float = 1f, bold: Boolean = false): TextStyle {
    val size = LocalTermSize.current * scale
    return TextStyle(
        fontFamily = Mono,
        fontSize = size.sp,
        lineHeight = (size * 1.4f).sp,
        color = color,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    )
}

@Composable
fun T(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Hx.text,
    scale: Float = 1f,
    bold: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = true,
    overflow: TextOverflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
) = Text(
    text, modifier, style = termStyle(color, scale, bold),
    maxLines = maxLines, softWrap = softWrap, overflow = overflow,
)

@Composable
fun T(text: AnnotatedString, modifier: Modifier = Modifier, color: Color = Hx.text, scale: Float = 1f) =
    Text(text, modifier, style = termStyle(color, scale))

@Composable
fun HermesTheme(fontSize: Int, content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = Hx.title, onPrimary = Hx.bg, secondary = Hx.accent,
        background = Hx.bg, onBackground = Hx.text, surface = Hx.panel, onSurface = Hx.text,
        error = Hx.error,
    )
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalTermSize provides fontSize,
            LocalTextSelectionColors provides TextSelectionColors(Hx.title, Hx.title.copy(alpha = 0.35f)),
            content = content,
        )
    }
}
