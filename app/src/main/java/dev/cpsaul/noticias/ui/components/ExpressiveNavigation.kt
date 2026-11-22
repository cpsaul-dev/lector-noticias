package dev.cpsaul.noticias.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.cpsaul.noticias.R
import dev.cpsaul.noticias.data.local.UserProfile
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class NavDestination(
    val index: Int,
    val labelRes: Int,
    val icon: ImageVector
)

/**
 * §1 y §2 Cabecera principal: fila de 64 dp bajo barra de estado, título a 16 dp,
 * fecha localizada pegada debajo y avatar redondo de 40 dp a 12 dp del borde derecho.
 */
@Composable
fun MainHeader(
    title: String,
    profile: UserProfile,
    onProfileClick: () -> Unit,
    actions: @Composable () -> Unit = {}
) {
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]
    val datePattern = stringResource(R.string.fecha_cabecera)
    val formattedDate = remember(locale, datePattern) {
        runCatching {
            LocalDate.now()
                .format(DateTimeFormatter.ofPattern(datePattern, locale))
                .replaceFirstChar { it.titlecase(locale) }
        }.getOrDefault("")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(start = 16.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (formattedDate.isNotBlank()) {
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            actions()
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onProfileClick),
                contentAlignment = Alignment.Center
            ) {
                Avatar(photo = profile.photoBytes, name = profile.displayName, size = 40.dp)
            }
        }
    }
}

/**
 * §6.2 Píldora flotante Material 3 Expressive (`CircleShape`, `tonalElevation = 3.dp`, `shadowElevation = 8.dp`).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PillNavigationBar(
    destinations: List<NavDestination>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .padding(6.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            destinations.forEach { dest ->
                val selected = dest.index == selectedIndex
                val label = stringResource(dest.labelRes)
                val containerColor by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                    label = "pill_bg"
                )
                val contentColor = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }

                Surface(
                    shape = CircleShape,
                    color = containerColor,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .animateContentSize(animationSpec = MaterialTheme.motionScheme.fastSpatialSpec())
                        .semantics { contentDescription = label }
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = { onSelect(dest.index) }
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .height(48.dp)
                            .padding(horizontal = if (selected) 16.dp else 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = dest.icon,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(24.dp)
                        )
                        // Excepción documentada en §6.2: tween permite retardo para esperar a que se abra el ancho.
                        AnimatedVisibility(
                            visible = selected,
                            enter = fadeIn(tween(durationMillis = 140, delayMillis = 170)),
                            exit = fadeOut(tween(durationMillis = 60))
                        ) {
                            Row {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = contentColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Renderizador limpio de Markdown local (encabezados H1–H3, citas, listas, párrafos e imagen local).
 */
@Composable
fun MarkdownArticleBody(
    markdown: String,
    imageLocalPath: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!imageLocalPath.isNullOrBlank()) {
            val bitmap = remember(imageLocalPath) {
                runCatching {
                    val file = File(imageLocalPath)
                    if (file.exists()) BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() else null
                }.getOrNull()
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
            }
        }

        val blocks = remember(markdown) {
            markdown.split(Regex("""\n\s*\n""")).map { it.trim() }.filter { it.isNotEmpty() }
        }
        blocks.forEach { block ->
            when {
                block.startsWith("### ") -> Text(
                    text = block.removePrefix("### "),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                block.startsWith("## ") -> Text(
                    text = block.removePrefix("## "),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                block.startsWith("# ") -> Text(
                    text = block.removePrefix("# "),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                block.startsWith("> ") -> Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = block.removePrefix("> "),
                        style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
                block.startsWith("- ") -> {
                    val lines = block.lines().map { it.trim().removePrefix("- ").trim() }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        lines.forEach { line ->
                            Text(
                                text = "• $line",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
                else -> Text(
                    text = block,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}
