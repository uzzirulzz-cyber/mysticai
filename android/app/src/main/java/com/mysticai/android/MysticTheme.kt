package com.mysticai.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val Ink = Color(0xFF090A0F)
internal val Surface = Color(0xFF11131B)
internal val Raised = Color(0xFF191B25)
internal val Hairline = Color(0xFF282A35)
internal val Champagne = Color(0xFFE6C98D)
internal val Lavender = Color(0xFFAA9BFF)
internal val SoftText = Color(0xFF9899A8)
internal val MainText = Color(0xFFF5F1EA)
internal val Mint = Color(0xFF79D9B5)
internal val Rose = Color(0xFFFF8C9A)

@Composable
fun MysticTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Champagne,
            onPrimary = Ink,
            secondary = Lavender,
            background = Ink,
            surface = Surface,
            onSurface = MainText,
            error = Rose,
        ),
        content = content,
    )
}
