package com.lippezin.dshagent.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ScreenHeading(title: String, subtitle: String, trailing: @Composable (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = C.Text)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = C.Dim)
            }
            trailing?.invoke()
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = C.Border)
    }
}

@Composable
fun SettingsSectionTitle(title: String, supporting: String? = null) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = C.Accent, fontWeight = FontWeight.SemiBold)
        if (supporting != null) {
            Spacer(Modifier.height(4.dp))
            Text(supporting, style = MaterialTheme.typography.bodySmall, color = C.Dim)
        }
    }
}

/** Ícone adequado para o tipo de arquivo (pela extensão). */
fun fileIconFor(name: String): ImageVector {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "zip", "tar", "gz", "rar", "7z" -> Icons.Outlined.Archive
        "png", "jpg", "jpeg", "gif", "webp", "svg", "heic" -> Icons.Outlined.Image
        "mp4", "webm", "mkv", "mov", "3gp" -> Icons.Outlined.Movie
        "mp3", "wav", "m4a", "ogg", "aac", "flac", "opus" -> Icons.Outlined.Audiotrack
        "kt", "kts", "java", "py", "js", "jsx", "ts", "tsx", "go", "rs", "c", "h", "cpp", "cs", "php",
        "swift", "sh", "bash", "html", "css", "xml", "json", "yml", "yaml", "gradle", "sql", "dart", "rb" -> Icons.Outlined.Code
        else -> Icons.Outlined.Description
    }
}

/** Logo do app: quadrado arredondado com gradiente da marca e ícone de brilho. */
@Composable
fun BrandMark(size: Dp = 36.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(C.Brand),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Outlined.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(size * 0.55f))
    }
}

/** Cápsula clicável com ícone, texto e seta (usada para modelo, esforço de raciocínio etc.). */
@Composable
fun Pill(
    icon: ImageVector?,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = C.Accent,
    maxTextWidth: Dp = 190.dp,
    showChevron: Boolean = true
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = C.Surface3,
        border = BorderStroke(1.dp, C.BorderStrong.copy(alpha = 0.6f))
    ) {
        Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text, color = C.Text2, style = MaterialTheme.typography.labelMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = maxTextWidth)
            )
            if (showChevron) {
                Spacer(Modifier.width(3.dp))
                Icon(Icons.Outlined.ExpandMore, null, tint = C.Dim, modifier = Modifier.size(15.dp))
            }
        }
    }
}

/** Três pontos pulsando: indicador de que o agente está trabalhando. */
@Composable
fun PulseDots(color: Color = C.Accent) {
    val transition = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 600, delayMillis = index * 160),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$index"
            )
            Box(Modifier.size(6.dp).clip(CircleShape).background(color.copy(alpha = alpha)))
        }
    }
}
