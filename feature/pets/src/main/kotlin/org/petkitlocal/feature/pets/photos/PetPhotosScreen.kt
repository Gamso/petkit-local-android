package org.petkitlocal.feature.pets.photos

import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.petkitlocal.core.designsystem.component.PkBackButton
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.feature.pets.PetsNavigation
import org.petkitlocal.feature.pets.R

/** What the photo screen lets its user do; the route wires each to the ViewModel or the phone. */
data class PetPhotosCallbacks(
    val onBack: () -> Unit = {},
    val onBackInFlow: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onAdd: () -> Unit = {},
    val onEditPet: () -> Unit = {},
    val onDelete: (Long) -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onDismissDelete: () -> Unit = {},
    val onTakePhoto: () -> Unit = {},
    val onChoosePhoto: () -> Unit = {},
    val onUseSquare: (CropSquare) -> Unit = {},
    val onChooseAnother: () -> Unit = {},
    val onKeep: () -> Unit = {},
    val onRetake: () -> Unit = {},
)

/** The photo to crop, as the screen has it. */
sealed interface CropImage {
    data object Loading : CropImage

    data class Ready(val image: ImageBitmap) : CropImage

    data object Unreadable : CropImage
}

/** Test tags of the photo screens. */
object PetPhotosTestTags {
    const val LIST = "pet_photos"
    const val ADD = "pet_photos_add"
    const val NOTICE = "pet_photos_notice"
    const val TIPS = "pet_photo_tips"
    const val TAKE = "pet_photo_tips_take"
    const val CHOOSE = "pet_photo_tips_choose"
    const val CROP = "pet_photo_crop"
    const val USE = "pet_photo_crop_use"
    const val REVIEW = "pet_photo_review"
    const val KEEP = "pet_photo_review_keep"
    const val RETAKE = "pet_photo_review_retake"

    fun face(id: Long) = "pet_photo_$id"

    fun delete(id: Long) = "pet_photo_delete_$id"
}

@Composable
internal fun PetPhotosRoute(navigation: PetsNavigation, viewModel: PetPhotosViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // The file the camera app is writing into, kept across the activity being recreated meanwhile.
    var capture by rememberSaveable { mutableStateOf<String?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val uri = capture
        if (taken && uri != null) viewModel.onPicked(uri)
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.onPicked(uri.toString())
    }
    val source = when (val step = uiState.step) {
        is PhotoStep.Crop -> step.source
        is PhotoStep.Uploading -> step.source
        else -> null
    }
    var bitmap by remember { mutableStateOf<Pair<String, Bitmap?>?>(null) }
    LaunchedEffect(source) {
        if (source != null && bitmap?.first != source) {
            val decoded = PetPhotoFiles.decode(context, source)
            bitmap = source to decoded
            if (decoded == null) viewModel.onUnreadable()
        }
    }
    val image = bitmap?.takeIf { it.first == source }?.second
    val cropImage = when {
        bitmap?.first != source -> CropImage.Loading
        image == null -> CropImage.Unreadable
        else -> remember(image) { CropImage.Ready(image.asImageBitmap()) }
    }
    BackHandler(enabled = uiState.step != PhotoStep.List) { viewModel.onBackInFlow() }
    PetPhotosScreen(
        uiState = uiState,
        cropImage = cropImage,
        callbacks = PetPhotosCallbacks(
            onBack = navigation.onBack,
            onBackInFlow = viewModel::onBackInFlow,
            onRetry = viewModel::onRetry,
            onAdd = viewModel::onAdd,
            onEditPet = { navigation.onEditPet(uiState.petId) },
            onDelete = viewModel::onDelete,
            onConfirmDelete = viewModel::onConfirmDelete,
            onDismissDelete = viewModel::onDismissDelete,
            onTakePhoto = {
                val uri = PetPhotoFiles.newCaptureUri(context)
                capture = uri.toString()
                try {
                    takePicture.launch(uri)
                } catch (_: ActivityNotFoundException) {
                    viewModel.onCameraUnavailable()
                }
            },
            onChoosePhoto = {
                pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onUseSquare = { square ->
                val original = image ?: return@PetPhotosCallbacks
                scope.launch { viewModel.onCropped(PetPhotoFiles.cropJpeg(original, square), square.side) }
            },
            onChooseAnother = viewModel::onChooseAnother,
            onKeep = viewModel::onKeep,
            onRetake = viewModel::onRetake,
        ),
    )
}

/**
 * A pet's reference photos and the way to add one (issue #98): the list; the tips with their
 * drawings, before the camera or the gallery; the square crop; what the server found wrong with
 * the photo, when it checks.
 */
@Composable
internal fun PetPhotosScreen(
    uiState: PetPhotosUiState,
    cropImage: CropImage,
    callbacks: PetPhotosCallbacks,
    modifier: Modifier = Modifier,
) {
    val name = uiState.content?.pet?.name.orEmpty()
    when (val step = uiState.step) {
        PhotoStep.List -> PhotoListPage(uiState, callbacks, modifier)

        is PhotoStep.Tips -> FlowPage(
            stringResource(R.string.pet_tips_title),
            callbacks.onBackInFlow,
            PetPhotosTestTags.TIPS,
            modifier,
        ) {
            TipsContent(step.noCamera, callbacks)
        }

        is PhotoStep.Crop -> FlowPage(
            stringResource(R.string.pet_crop_title),
            callbacks.onBackInFlow,
            PetPhotosTestTags.CROP,
            modifier,
        ) {
            CropContent(cropImage, step.problem, uploading = false, callbacks)
        }

        is PhotoStep.Uploading -> FlowPage(
            stringResource(R.string.pet_crop_title),
            callbacks.onBackInFlow,
            PetPhotosTestTags.CROP,
            modifier,
        ) {
            CropContent(cropImage, problem = null, uploading = true, callbacks)
        }

        is PhotoStep.Review -> FlowPage(
            stringResource(R.string.pet_review_title),
            callbacks.onBackInFlow,
            PetPhotosTestTags.REVIEW,
            modifier,
        ) {
            ReviewContent(step, name, callbacks)
        }
    }
}

/** A step of adding a photo: its title, the way back to the list, and its content, scrolling. */
@Composable
internal fun FlowPage(
    title: String,
    onBack: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val gutter = PkTheme.spacing.gutter
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
            .testTag(tag),
    ) {
        PageHeader(title, onBack)
        content()
    }
}

@Composable
internal fun PageHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PkBackButton(onClick = onBack)
        Spacer(Modifier.width(PkTheme.spacing.s))
        Text(
            title,
            style = PkTheme.typography.titleLarge,
            color = PkTheme.colors.onSurface,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
    }
}
