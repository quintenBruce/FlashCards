package com.qcbru.flashcards

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val Light = lightColorScheme(primary=Color(0xFF285E59),onPrimary=Color.White,
    primaryContainer=Color(0xFFD9EEE8),onPrimaryContainer=Color(0xFF123D39),
    secondary=Color(0xFF506864),secondaryContainer=Color(0xFFE3EFEB),
    tertiary=Color(0xFF855B46),tertiaryContainer=Color(0xFFFFE5D7),
    background=Color(0xFFF6F8F5),onBackground=Color(0xFF182523),surface=Color.White,onSurface=Color(0xFF182523),
    surfaceVariant=Color(0xFFE8EFEB),onSurfaceVariant=Color(0xFF4B5B56),outlineVariant=Color(0xFFDDE5DF))
private val Dark = darkColorScheme(primary=Color(0xFFA8D9CE),onPrimary=Color(0xFF0C3934),
    primaryContainer=Color(0xFF24544E),onPrimaryContainer=Color(0xFFD2F2E9),
    secondary=Color(0xFFB8D0C9),secondaryContainer=Color(0xFF354D47),
    tertiary=Color(0xFFE8BBA1),tertiaryContainer=Color(0xFF684730),
    background=Color(0xFF111C1A),onBackground=Color(0xFFE1EDE7),surface=Color(0xFF192522),onSurface=Color(0xFFE1EDE7),
    surfaceVariant=Color(0xFF33433D),onSurfaceVariant=Color(0xFFC0CEC6),outlineVariant=Color(0xFF3B4B44))
@Composable fun FlashTheme(theme: String, content: @Composable () -> Unit) {
    val dark = theme == "dark" || theme == "system" && isSystemInDarkTheme()
    val type = Typography()
    MaterialExpressiveTheme(motionScheme=MotionScheme.expressive(),colorScheme=if(dark) Dark else Light,
        shapes=Shapes(small=RoundedCornerShape(12.dp),medium=RoundedCornerShape(18.dp),large=RoundedCornerShape(26.dp)),
        typography=type.copy(headlineMedium=type.headlineMedium.copy(fontWeight=FontWeight.SemiBold),titleLarge=type.titleLarge.copy(fontWeight=FontWeight.SemiBold)), content=content)
}
