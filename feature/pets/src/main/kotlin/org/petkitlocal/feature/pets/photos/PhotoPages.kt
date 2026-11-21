package org.petkitlocal.feature.pets.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.petkitlocal.core.designsystem.component.PkButton
import org.petkitlocal.core.designsystem.component.PkButtonStyle
import org.petkitlocal.core.designsystem.component.PkCard
import org.petkitlocal.core.designsystem.component.PkConfirmDialog
import org.petkitlocal.core.designsystem.component.PkEmptyView
import org.petkitlocal.core.designsystem.component.PkIconButton
import org.petkitlocal.core.designsystem.component.PkLoadingView
import org.petkitlocal.core.designsystem.component.PkOfflineBanner
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.model.PetFace
import org.petkitlocal.core.domain.pets.FaceWarning
import org.petkitlocal.core.domain.pets.PetManagementRules
import org.petkitlocal.feature.pets.R
import org.petkitlocal.feature.pets.writeErrorMessage

/** The pet's photos: what they are for, each with what its devices made of it, and Add. */
@Composable
internal fun PhotoListPage(uiState: PetPhotosUiState, callbacks: PetPhotosCallbacks, modifier: Modifier = Modifier) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val gutter = PkTheme.spacing.gutter
    val content = uiState.content
    Column(
        verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.cardGap),
        modifier = modifier
            .fillMaxSize()
            .background(PkTheme.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(
                top = insets.calculateTopPadding() + gutter,
                bottom =
                    insets.calculateBottomPadding() + PkTheme.spacing.xl,
            )
            .padding(horizontal = gutter)
            .testTag(PetPhotosTestTags.LIST),
    ) {
        PageHeader(stringResource(R.string.pet_photos_title, content?.pet?.name.orEmpty()), callbacks.onBack)
        when {
            !uiState.cacheLoaded -> PkLoadingView(cards = 2)
            content == null -> PkEmptyView(stringResource(R.string.pet_gone))
            else -> PhotoList(uiState, callbacks)
        }
    }
    if (uiState.confirmingDelete != null) {
        PkConfirmDialog(
            title = stringResource(R.string.pet_photo_delete_title),
            message = stringResource(R.string.pet_photo_delete_message),
            confirmLabel = stringResource(R.string.pet_editor_delete_confirm),
            onConfirm = callbacks.onConfirmDelete,
            onDismiss = callbacks.onDismissDelete,
        )
    }
}

@Composable
private fun PhotoList(uiState: PetPhotosUiState, callbacks: PetPhotosCallbacks) {
    val content = checkNotNull(uiState.content)
    val colors = PkTheme.colors
    if (uiState.loadError != null) {
        PkOfflineBanner(message = stringResource(R.string.pets_refresh_failed))
    }
    PkCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.pet_photos_intro), style = PkTheme.typography.bodyMedium, color = colors.onSurface)
        uiState.maxFaces?.let {
            Text(
                stringResource(R.string.pet_photos_limit, it),
                style = PkTheme.typography.labelLarge,
                color = colors.onSurfaceMuted,
                modifier = Modifier.padding(top = PkTheme.spacing.xxs),
            )
        }
        if (content.devices.isEmpty()) {
            Row(Modifier.padding(top = PkTheme.spacing.s), verticalAlignment = Alignment.Top) {
                Icon(PkIcons.Warning, contentDescription = null, tint = colors.warning, modifier = Modifier.size(20.dp))
                Text(
                    stringResource(R.string.pet_photos_no_device, content.pet.name),
                    style = PkTheme.typography.bodyMedium,
                    color = colors.onSurface,
                    modifier = Modifier.padding(start = PkTheme.spacing.xs),
                )
            }
            PkButton(
                stringResource(R.string.pet_photos_choose_devices),
                onClick = callbacks.onEditPet,
                style = PkButtonStyle.Text,
                icon = PkIcons.Edit,
            )
        }
    }
    val faces = uiState.faces
    if (faces.isEmpty()) {
        Text(
            stringResource(R.string.pet_photos_empty),
            style = PkTheme.typography.bodyLarge,
            color = colors.onSurfaceMuted,
        )
    }
    // Two across: wide enough for what each camera made of the photo.
    val perRow = 2
    faces.withIndex().chunked(perRow).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs)) {
            row.forEach { (index, face) ->
                FaceTile(index + 1, face, uiState, callbacks.onDelete, Modifier.weight(1f))
            }
            repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs)) {
        val uploading = uiState.step is PhotoStep.Uploading
        PkButton(
            stringResource(if (uploading) R.string.pet_photos_uploading else R.string.pet_photos_add),
            onClick = callbacks.onAdd,
            enabled = !uploading,
            icon = PkIcons.Add,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(PetPhotosTestTags.ADD),
        )
        uiState.notice?.let { notice ->
            val (text, color) = when (notice) {
                is PhotoNotice.AtLimit ->
                    stringResource(R.string.pet_photos_at_limit, content.pet.name, notice.max) to
                        colors.onWarningContainer

                PhotoNotice.Added -> stringResource(R.string.pet_photos_added) to colors.success

                is PhotoNotice.Failed -> writeErrorMessage(notice.error) to colors.danger
            }
            Text(
                text,
                style = PkTheme.typography.bodyMedium,
                color = color,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(PkTheme.shapes.tile)
                    .background(if (notice is PhotoNotice.AtLimit) colors.warningContainer else colors.background)
                    .padding(if (notice is PhotoNotice.AtLimit) PkTheme.spacing.s else 0.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag(PetPhotosTestTags.NOTICE),
            )
        }
    }
}

private const val LARGE_FONT = 1.2f

/** One photo, its Delete, and for each of the pet's cameras whether it took the photo in. */
@Composable
private fun FaceTile(
    number: Int,
    face: PetFace,
    uiState: PetPhotosUiState,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = checkNotNull(uiState.content)
    val colors = PkTheme.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.xxs),
        modifier = modifier.testTag(PetPhotosTestTags.face(face.id)),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(PkTheme.shapes.tile)
                .background(colors.surfaceMuted),
        ) {
            Icon(
                PkIcons.Photo,
                contentDescription = null,
                tint = colors.onSurfaceMuted,
                modifier = Modifier.align(Alignment.Center),
            )
            AsyncImage(
                model = face.photoUrl,
                contentDescription = stringResource(R.string.pet_photo_description, number, content.pet.name),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (uiState.deleting != face.id) {
                PkIconButton(
                    PkIcons.Delete,
                    contentDescription = stringResource(R.string.pet_photo_delete, number),
                    onClick = { onDelete(face.id) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(PkTheme.spacing.xxs)
                        .testTag(PetPhotosTestTags.delete(face.id)),
                )
            }
        }
        PetManagementRules.faceStatus(face, content.allDevices)?.forEach { status ->
            StatusLine(
                icon = if (status.accepted) PkIcons.CheckCircle else PkIcons.Hourglass,
                tint = if (status.accepted) colors.success else colors.onSurfaceMuted,
                text = stringResource(
                    if (status.accepted) R.string.pet_photo_recognised_by else R.string.pet_photo_waiting_for,
                    status.device.modelName,
                ),
            )
        }
    }
}

@Composable
private fun StatusLine(icon: ImageVector, tint: androidx.compose.ui.graphics.Color, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Text(
            text,
            style = PkTheme.typography.labelMedium,
            color = PkTheme.colors.onSurface,
            modifier = Modifier.padding(start = PkTheme.spacing.xxs),
        )
    }
}

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

/** The square crop: the photo under its frame, zoom buttons for those who cannot pinch, and Use. */
@Composable
internal fun CropContent(image: CropImage, problem: CropProblem?, uploading: Boolean, callbacks: PetPhotosCallbacks) {
    val colors = PkTheme.colors
    Text(stringResource(R.string.pet_crop_hint), style = PkTheme.typography.bodyLarge, color = colors.onSurface)
    when (image) {
        CropImage.Loading -> {
            Text(
                stringResource(R.string.pet_crop_loading),
                style = PkTheme.typography.bodyMedium,
                color = colors.onSurfaceMuted,
            )
            PkLoadingView(cards = 1)
        }

        CropImage.Unreadable -> ProblemText(stringResource(R.string.pet_crop_unreadable))

        is CropImage.Ready -> ReadyCrop(image, problem, uploading, callbacks)
    }
    PkButton(
        stringResource(R.string.pet_crop_other),
        onClick = callbacks.onChooseAnother,
        style = PkButtonStyle.Secondary,
        enabled = !uploading,
        icon = PkIcons.PhotoLibrary,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ReadyCrop(
    image: CropImage.Ready,
    problem: CropProblem?,
    uploading: Boolean,
    callbacks: PetPhotosCallbacks,
) {
    val state = rememberCropState(image.image)
    PhotoCropper(image.image, state, stringResource(R.string.pet_crop_area), Modifier.clip(PkTheme.shapes.card))
    Row(
        horizontalArrangement = Arrangement.spacedBy(PkTheme.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PkIconButton(PkIcons.Remove, stringResource(R.string.pet_crop_zoom_out), onClick = {
            state.zoomBy(1 / ZOOM_STEP)
        })
        PkIconButton(PkIcons.Add, stringResource(R.string.pet_crop_zoom_in), onClick = { state.zoomBy(ZOOM_STEP) })
    }
    when (problem) {
        null -> Unit

        CropProblem.Unreadable -> ProblemText(stringResource(R.string.pet_crop_unreadable))

        is CropProblem.TooSmall -> ProblemText(
            stringResource(R.string.pet_crop_too_small, problem.side, PetPhotosViewModel.MIN_SIDE),
        )

        is CropProblem.Failed -> ProblemText(writeErrorMessage(problem.error))
    }
    PkButton(
        stringResource(if (uploading) R.string.pet_photos_uploading else R.string.pet_crop_use),
        onClick = { callbacks.onUseSquare(state.square()) },
        enabled = !uploading,
        icon = PkIcons.Check,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(PetPhotosTestTags.USE),
    )
}

private const val ZOOM_STEP = 1.25f

@Composable
private fun ProblemText(text: String) {
    Text(
        text,
        style = PkTheme.typography.bodyMedium,
        color = PkTheme.colors.danger,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** What the server found wrong with the photo it stored, in the app's words: keep it, or take another. */
@Composable
internal fun ReviewContent(step: PhotoStep.Review, petName: String, callbacks: PetPhotosCallbacks) {
    val colors = PkTheme.colors
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .widthIn(max = 200.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(PkTheme.shapes.tile)
                .background(colors.surfaceMuted),
        ) {
            Icon(
                PkIcons.Photo,
                contentDescription = null,
                tint = colors.onSurfaceMuted,
                modifier = Modifier.align(Alignment.Center),
            )
            AsyncImage(
                model = step.face.photoUrl,
                contentDescription = stringResource(R.string.pet_photo_description, 1, petName),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
    PkCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.pet_review_intro), style = PkTheme.typography.bodyMedium, color = colors.onSurface)
        step.warnings.forEach { warning ->
            Row(Modifier.padding(top = PkTheme.spacing.xs), verticalAlignment = Alignment.Top) {
                Icon(PkIcons.Warning, contentDescription = null, tint = colors.warning, modifier = Modifier.size(20.dp))
                Text(
                    stringResource(warning.message),
                    style = PkTheme.typography.bodyLarge,
                    color = colors.onSurface,
                    modifier = Modifier.padding(start = PkTheme.spacing.xs),
                )
            }
        }
    }
    PkButton(
        stringResource(R.string.pet_review_keep),
        onClick = callbacks.onKeep,
        icon = PkIcons.Check,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(PetPhotosTestTags.KEEP),
    )
    PkButton(
        stringResource(R.string.pet_review_retake),
        onClick = callbacks.onRetake,
        style = PkButtonStyle.Secondary,
        enabled = !step.retaking,
        icon = PkIcons.PhotoCamera,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(PetPhotosTestTags.RETAKE),
    )
}

/** A warning of the server (petkit-local#132), in the app's words. */
private val FaceWarning.message: Int
    get() = when (this) {
        FaceWarning.Blurry -> R.string.pet_review_blurry
        FaceWarning.TooDark -> R.string.pet_review_too_dark
        FaceWarning.Overexposed -> R.string.pet_review_overexposed
        FaceWarning.NotSquare -> R.string.pet_review_not_square
    }
