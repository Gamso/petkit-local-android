package org.petkitlocal.feature.device.feeder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.feature.device.R

/**
 * A portion count with a minus and a plus, each a 48 dp round button TalkBack names. [label] says
 * what is counted (a hopper); a press past [min] or [max] changes nothing.
 */
@Composable
internal fun PortionStepper(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.feeder_stepper_value, label, value)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        StepButton(PkIcons.Remove, stringResource(R.string.feeder_less, label)) {
            onChange((value - 1).coerceAtLeast(min))
        }
        Text(
            value.toString(),
            style = PkTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(min = 40.dp)
                .semantics {
                    contentDescription = description
                    liveRegion = LiveRegionMode.Polite
                }
                .testTag(FeederTestTags.stepper(label)),
        )
        StepButton(PkIcons.Add, stringResource(R.string.feeder_more, label)) { onChange((value + 1).coerceAtMost(max)) }
    }
}

@Composable
private fun StepButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = PkTheme.colors.surfaceMuted,
        contentColor = PkTheme.colors.onSurface,
        modifier = Modifier.size(PkTheme.spacing.minTouchTarget),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(22.dp))
        }
    }
}
