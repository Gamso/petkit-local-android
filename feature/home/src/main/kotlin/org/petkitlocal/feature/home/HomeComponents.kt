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
import androidx.compose.ui.platform.testTag
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
import org.petkitlocal.core.designsystem.component.PkButton
import org.petkitlocal.core.designsystem.component.PkButtonStyle
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.home.AlertKind
import org.petkitlocal.core.domain.home.DeviceAlert
import org.petkitlocal.core.domain.home.PetOverview
import org.petkitlocal.core.ui.PetAvatar
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

/**
 * Notifications are on for this phone but Android 13+ does not let them show (issue #23): one
 * line saying so, Android's prompt behind "Allow", and "Not now", which puts it away for the rest
 * of the session. Drawn as the alert banners are, in the same place.
 */
@Composable
internal fun NotificationPermissionBanner(onAllow: () -> Unit, onNotNow: () -> Unit, modifier: Modifier = Modifier) {
    val colors = PkTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(PkTheme.shapes.tile)
            .background(colors.warningContainer)
            .padding(start = 14.dp, end = PkTheme.spacing.xs, top = PkTheme.spacing.s, bottom = PkTheme.spacing.xxs)
            .testTag(HomeTestTags.NOTIFICATION_BANNER),
    ) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(end = PkTheme.spacing.xs)) {
            Icon(PkIcons.Warning, contentDescription = null, tint = colors.warning, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(10.dp))
            Text(
                stringResource(R.string.home_notifications_blocked),
                style = PkTheme.typography.bodyMedium,
                color = colors.onWarningContainer,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(PkTheme.spacing.xxs, Alignment.End),
            modifier = Modifier.fillMaxWidth(),
        ) {
            PkButton(
                stringResource(R.string.home_notifications_not_now),
                onClick = onNotNow,
                style = PkButtonStyle.Text,
                modifier = Modifier.testTag(HomeTestTags.NOTIFICATION_NOT_NOW),
            )
            PkButton(
                stringResource(R.string.home_notifications_allow),
                onClick = onAllow,
                style = PkButtonStyle.Text,
                modifier = Modifier.testTag(HomeTestTags.NOTIFICATION_ALLOW),
            )
        }
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
            PetAvatar(
                pet.pet.name,
                pet.pet.photoUrl,
                pet.colorIndex,
                44.dp,
                initialStyle = PkTheme.typography.titleLarge,
            )
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
