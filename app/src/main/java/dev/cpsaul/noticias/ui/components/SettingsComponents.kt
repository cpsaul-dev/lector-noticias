package dev.cpsaul.noticias.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cpsaul.noticias.R

/**
 * §1.1 Barra estándar de subpantallas: TopAppBar con botón de volver y título a la izquierda (28 sp, Medium).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubScreenTopAppBar(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 28.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back)
                )
            }
        },
        actions = actions
    )
}

/**
 * §4 Avatar de perfil estándar (40 dp en cabecera, 84–96 dp en perfil).
 */
@Composable
fun Avatar(
    photo: ByteArray?,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val image = remember(photo) {
        photo?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }.getOrNull() }
    }
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier.size(size)
    ) {
        when {
            image != null -> Image(
                bitmap = image,
                contentDescription = stringResource(R.string.nav_profile),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            name.isNotBlank() -> Box(contentAlignment = Alignment.Center) {
                Text(
                    text = name.trim().first().uppercase(),
                    style = if (size > 60.dp) {
                        MaterialTheme.typography.displaySmall.copy(fontSize = 36.sp, fontWeight = FontWeight.Normal)
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            else -> Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = stringResource(R.string.nav_profile),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(size * 0.18f)
            )
        }
    }
}

/**
 * §3.2 Etiqueta de sección en MAYÚSCULAS (`labelMedium`, secundario, 24 dp lados, 18 dp arriba, 6 dp abajo).
 */
@Composable
fun SettingsSectionLabel(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.6.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 6.dp)
    )
}

/**
 * §3.2 Tarjeta contenedora de filas (`surfaceContainer`, esquinas 16 dp, margen lateral 12 dp).
 */
@Composable
fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        Column(content = content)
    }
}

@Composable
fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
fun SettingsIconTile(
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    size: Dp = 30.dp
) {
    Surface(
        shape = RoundedCornerShape(percent = 26),
        color = containerColor,
        modifier = Modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(size * 0.54f)
            )
        }
    }
}

/**
 * §3.3 Fila estándar de Ajustes (alto mín. 52 dp, icono en cuadrado de 30 dp, un único control a la derecha).
 */
@Composable
fun SettingsRow(
    icon: ImageVector,
    iconContainerColor: Color,
    iconContentColor: Color,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val rowModifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 52.dp)
        .let { base ->
            if (onClick != null) base.clickable(role = Role.Button, onClick = onClick) else base
        }
        .padding(horizontal = 12.dp, vertical = 8.dp)

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIconTile(
            icon = icon,
            containerColor = iconContainerColor,
            contentColor = iconContentColor
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (!value.isNullOrBlank()) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailing()
        } else if (showChevron) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
