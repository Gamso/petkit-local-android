package org.petkitlocal.feature.pets.photos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.petkitlocal.core.designsystem.component.PkButton
import org.petkitlocal.core.designsystem.component.PkButtonStyle
import org.petkitlocal.core.designsystem.component.PkCard
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.feature.pets.R

/** Before the camera or the gallery: what a good photo is, drawn, and what it is not. */
@Composable
internal fun TipsContent(noCamera: Boolean, callbacks: PetPhotosCallbacks) {
    val colors = PkTheme.colors
    Text(stringResource(R.string.pet_tips_intro), style = PkTheme.typography.bodyLarge, color = colors.onSurface)
    PkCard(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TipIllustration(TipSketch.Good, Modifier.widthIn(max = 220.dp))
        }
        ExampleLabel(
            good = true,
            stringResource(R.string.pet_tips_good_label),
            Modifier.padding(top = PkTheme.spacing.s),
        )
        Text(
            stringResource(R.string.pet_tips_good),
            style = PkTheme.typography.bodyMedium,
            color = colors.onSurface,
            modifier = Modifier.padding(top = PkTheme.spacing.xxs),
        )
    }
    BadExamplesCard()
    if (noCamera) {
        Text(
            stringResource(R.string.pet_tips_no_camera),
            style = PkTheme.typography.bodyMedium,
            color = colors.danger,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    PkButton(
        stringResource(R.string.pet_tips_take),
        onClick = callbacks.onTakePhoto,
        icon = PkIcons.PhotoCamera,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(PetPhotosTestTags.TAKE),
    )
    PkButton(
        stringResource(R.string.pet_tips_choose),
        onClick = callbacks.onChoosePhoto,
        style = PkButtonStyle.Secondary,
        icon = PkIcons.PhotoLibrary,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(PetPhotosTestTags.CHOOSE),
    )
}

/** What not to send, each drawn with its cross. */
@Composable
private fun BadExamplesCard() {
    PkCard(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.pet_tips_bad),
            style = PkTheme.typography.titleMedium,
            modifier = Modifier
                .padding(bottom = PkTheme.spacing.s)
                .semantics { heading() },
        )
        val examples = listOf(
            TipSketch.TwoPets to R.string.pet_tips_two_pets,
            TipSketch.Side to R.string.pet_tips_side,
            TipSketch.Far to R.string.pet_tips_far,
            TipSketch.Dark to R.string.pet_tips_dark,
            TipSketch.Blurry to R.string.pet_tips_blurry,
        )
        val perRow = if (LocalDensity.current.fontScale > LARGE_FONT) 2 else 3
        Column(verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.s)) {
            examples.chunked(perRow).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs)) {
                    row.forEach { (sketch, label) ->
                        Column(
                            verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.xxs),
                            modifier = Modifier
                                .weight(1f)
                                .semantics(mergeDescendants = true) {},
                        ) {
                            TipIllustration(sketch)
                            ExampleLabel(good = false, stringResource(label))
                        }
                    }
                    repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** "Good" with a check, "Not good" with a cross: never the colour alone. */
@Composable
private fun ExampleLabel(good: Boolean, text: String, modifier: Modifier = Modifier) {
    val colors = PkTheme.colors
    Row(verticalAlignment = Alignment.Top, modifier = modifier) {
        Icon(
            if (good) PkIcons.CheckCircle else PkIcons.Cancel,
            contentDescription = if (good) {
                stringResource(
                    R.string.pet_tips_good_label,
                )
            } else {
                stringResource(R.string.pet_tips_bad_label)
            },
            tint = if (good) colors.success else colors.danger,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text,
            style = PkTheme.typography.labelLarge,
            color = colors.onSurface,
            modifier = Modifier.padding(start = PkTheme.spacing.xxs),
        )
    }
}

private const val LARGE_FONT = 1.2f
