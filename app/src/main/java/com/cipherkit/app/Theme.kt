package com.cipherkit.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object CK {
    val Bg = Color(0xFF05080A)
    val Surface = Color(0xFF0C1215)
    val Field = Color(0xFF101A1E)
    val Surface2 = Color(0xFF131E22)
    val Outline = Color(0xFF1F3136)
    val Accent = Color(0xFF2DE2D0)
    val AccentDim = Color(0xFF1B8C82)
    val Text = Color(0xFFE8F4F3)
    val Muted = Color(0xFF8AA6A4)
    val Error = Color(0xFFFF7B7B)
}

@Composable
fun CipherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = CK.Accent,
            onPrimary = Color.Black,
            background = CK.Bg,
            onBackground = CK.Text,
            surface = CK.Surface,
            onSurface = CK.Text,
            surfaceVariant = CK.Surface2,
            onSurfaceVariant = CK.Muted,
            outline = CK.Outline,
            error = CK.Error,
        ),
        content = content,
    )
}
