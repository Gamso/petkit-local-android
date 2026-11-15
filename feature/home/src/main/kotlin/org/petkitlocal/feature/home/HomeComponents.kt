package org.petkitlocal.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.home.AlertKind
import org.petkitlocal.core.domain.home.DeviceAlert
import org.petkitlocal.core.domain.home.PetOverview
import org.petkitlocal.core.ui.formatWeightKg
import org.petkitlocal.core.ui.rememberServerLabels

/** One thing needing attention (docs/DESIGN.md, AlertBanner): what, on which device, and a way there. */
@Composable
internal fun AlertBanner(alert: DeviceAlert, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = PkTheme.colors
    val title = alertTitle(alert.kind, rememberServerLabels())
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = PkTheme.spacing.minTouchTarget)
            .clip(PkTheme.shapes.tile)
            .background(if (alert.kind == AlertKind.Offline) colors.surfaceMuted else colors.warningContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = PkTheme.spacing.s),
    ) {
        val offline = alert.kind == AlertKind.Offline
        val content = if (offline) colors.onSurface else colors.onWarningContainer
        Icon(
            if (offline) PkIcons.Offline else PkIcons.Warning,
            contentDescription = null,
            tint = if (offline) colors.onSurfaceMuted else colors.warning,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(10.dp))
        Text(
            buildAnnotatedString {
                val line = stringResource(R.string.home_alert_line, title, alert.deviceName)
                val start = line.indexOf(title)
                append(line)
                if (start >= 0) addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, start + title.length)
            },
            style = PkTheme.typography.bodyMedium,
            color = content,
            modifier = Modifier.weight(1f),
        )
        Icon(PkIcons.ChevronRight, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
    }
}

/** A pet on Home (docs/DESIGN.md, PetChip): avatar, name, weight and visits today. */
@Composable
internal fun PetChip(pet: PetOverview, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = PkTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    val visits = pluralStringResource(R.plurals.home_pet_visits, pet.visitsToday, pet.visitsToday)
    val weight = pet.pet.weightG?.let { formatWeightKg(it, locale) }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = colors.surface,
        contentColor = colors.onSurface,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(PkTheme.spacing.s),
        ) {
            PetAvatar(pet)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    pet.pet.name,
                    style = PkTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (weight != null) stringResource(R.string.home_pet_line, weight, visits) else visits,
                    style = PkTheme.typography.labelMedium,
                    color = colors.onSurfaceMuted,
                )
            }
        }
    }
}

/** The pet's photo, over its initial on its colour, which is what shows until (or unless) it loads. */
@Composable
private fun PetAvatar(pet: PetOverview) {
    val color = PkTheme.colors.pet(pet.colorIndex)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = AVATAR_TINT)),
    ) {
        Text(
            pet.pet.name.take(1).uppercase(),
            style = PkTheme.typography.titleLarge,
            color = color,
        )
        if (pet.pet.photoUrl != null) {
            AsyncImage(
                model = pet.pet.photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(44.dp),
            )
        }
    }
}

private const val AVATAR_TINT = 0.16f

/** A section title, with an optional action at its end. */
@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        Text(
            text,
            style = PkTheme.typography.headlineSmall,
            color = PkTheme.colors.onSurface,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        action?.invoke()
    }
}
