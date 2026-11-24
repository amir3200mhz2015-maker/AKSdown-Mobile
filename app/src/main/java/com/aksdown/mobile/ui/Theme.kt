package com.aksdown.mobile.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Aks {
    val Bg = Color(0xFF0B0F17)
    val Card = Color(0xFF151C2C)
    val Card2 = Color(0xFF1C2538)
    val Line = Color(0xFF26314A)
    val Blue = Color(0xFF2563EB)
    val Blue2 = Color(0xFF3B82F6)
    val Ink = Color.White
    val Muted = Color(0xFF93A0B8)
    val Ok = Color(0xFF22C55E)
    val Bad = Color(0xFFF87171)
    val Warn = Color(0xFFFBBF24)
}

private val scheme = darkColorScheme(
    primary = Aks.Blue, onPrimary = Color.White, secondary = Aks.Blue2,
    background = Aks.Bg, onBackground = Aks.Ink, surface = Aks.Card, onSurface = Aks.Ink,
    surfaceVariant = Aks.Card2, onSurfaceVariant = Aks.Muted, outline = Aks.Line,
    surfaceContainer = Aks.Card, surfaceContainerHigh = Aks.Card2, surfaceContainerHighest = Aks.Card2,
    error = Aks.Bad,
)

private val type = Typography(
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 28.sp),
    titleLarge = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun AksTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, typography = type, content = content)
