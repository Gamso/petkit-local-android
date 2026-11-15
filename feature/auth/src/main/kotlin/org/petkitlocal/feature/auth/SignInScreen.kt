package org.petkitlocal.feature.auth

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.petkitlocal.core.designsystem.component.PkButton
import org.petkitlocal.core.designsystem.component.PkPreviewSurface
import org.petkitlocal.core.designsystem.component.PkPreviews
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.auth.SignInFailure

/** The callbacks of [SignInScreen], one per user intent. */
internal class SignInActions(
    val onAddressChange: (String) -> Unit = {},
    val onPasswordChange: (String) -> Unit = {},
    val onPhoneNameChange: (String) -> Unit = {},
    val onTogglePasswordVisibility: () -> Unit = {},
    val onTogglePhoneName: () -> Unit = {},
    val onSubmit: () -> Unit = {},
    val onAllowLocalNetwork: () -> Unit = {},
    val onOpenAppSettings: () -> Unit = {},
)

/** Where the screen puts the cursor when a field's error is shown. */
internal class SignInFocus(
    val address: FocusRequester = FocusRequester(),
    val password: FocusRequester = FocusRequester(),
)

@Composable
internal fun SignInRoute(viewModel: SignInViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focus = remember { SignInFocus() }
    val askLocalNetwork = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.onLocalNetworkResult(it)
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                SignInEffect.RequestLocalNetworkPermission -> localNetworkPermission()?.let(askLocalNetwork::launch)
                    ?: viewModel.onLocalNetworkResult(granted = true)

                is SignInEffect.Focus -> when (effect.field) {
                    SignInField.Address -> focus.address.requestFocus()
                    SignInField.Password -> focus.password.requestFocus()
                }
            }
        }
    }
    SignInScreen(
        uiState,
        SignInActions(
            onAddressChange = viewModel::onAddressChange,
            onPasswordChange = viewModel::onPasswordChange,
            onPhoneNameChange = viewModel::onPhoneNameChange,
            onTogglePasswordVisibility = viewModel::onTogglePasswordVisibility,
            onTogglePhoneName = viewModel::onTogglePhoneName,
            onSubmit = viewModel::onSubmit,
            onAllowLocalNetwork = viewModel::onAllowLocalNetworkClick,
            onOpenAppSettings = { openAppSettings(context) },
        ),
        focus = focus,
    )
}

/** Android 17's local network permission, or null where there is none to ask for. */
private fun localNetworkPermission(): String? =
    if (Build.VERSION.SDK_INT >= LOCAL_NETWORK_PERMISSION_SDK) Manifest.permission.ACCESS_LOCAL_NETWORK else null

/** Android 17 (API 37) brought `ACCESS_LOCAL_NETWORK`. */
private const val LOCAL_NETWORK_PERMISSION_SDK = 37

/** This app's page in Android's settings, where a refused permission can be granted again. */
private fun openAppSettings(context: Context) {
    val intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No settings app to open: the message already says what to allow.
    }
}

/**
 * Sign-in as one group, a little above the middle of the window: the app's mark and name, then the
 * form. It scrolls from the top once it does not fit (the keyboard is up, a large font, a long
 * message).
 */
@Composable
internal fun SignInScreen(
    uiState: SignInUiState,
    actions: SignInActions,
    modifier: Modifier = Modifier,
    focus: SignInFocus = remember { SignInFocus() },
) {
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(PkTheme.colors.background)
            .safeDrawingPadding()
            .imePadding()
            .testTag(SignInTestTags.SCREEN),
    ) {
        BiasedCentre(
            bias = UPWARD_BIAS,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight),
        ) {
            SignInContent(uiState, actions, focus)
        }
    }
}

/** Where the group sits in the free height: 0 top, 0.5 middle. A little above the middle reads better. */
private const val UPWARD_BIAS = 0.4f

/** The mark, the form, the messages that go with it, and its button. */
@Composable
private fun SignInContent(uiState: SignInUiState, actions: SignInActions, focus: SignInFocus) {
    // The keyboard goes away on submit, so the answer below the form is seen.
    val focusManager = LocalFocusManager.current
    val submit = {
        focusManager.clearFocus()
        actions.onSubmit()
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.m),
        modifier = Modifier.fillMaxWidth().padding(PkTheme.spacing.gutter),
    ) {
        AppWelcome(Modifier.padding(bottom = PkTheme.spacing.xs))
        uiState.sessionEnded?.let { SessionEndedBanner(it) }
        SignInForm(uiState, actions, focus, submit)
        if (uiState.localNetworkRationale) {
            Notice(
                text = stringResource(R.string.auth_local_network_rationale),
                action = stringResource(R.string.auth_local_network_allow) to actions.onAllowLocalNetwork,
                modifier = Modifier.testTag(SignInTestTags.LOCAL_NETWORK),
            )
        }
        if (uiState.failure == SignInFailure.LocalNetworkDenied) {
            Notice(
                text = stringResource(R.string.auth_error_local_network_denied),
                action = stringResource(R.string.auth_open_app_settings) to actions.onOpenAppSettings,
                modifier = Modifier.testTag(SignInTestTags.ERROR),
            )
        }
        generalErrorMessage(uiState.failure)?.let { ErrorCard(it) }
        PkButton(
            text = submitLabel(uiState),
            onClick = submit,
            enabled = uiState.canSubmit,
            modifier = Modifier.fillMaxWidth().testTag(SignInTestTags.SUBMIT),
        )
    }
}

/** Tags for UI tests and the end-to-end flows. */
object SignInTestTags {
    const val SCREEN = "sign_in"
    const val ADDRESS = "sign_in_address"
    const val PASSWORD = "sign_in_password"
    const val PHONE_NAME = "sign_in_phone_name"
    const val PHONE_NAME_ROW = "sign_in_phone_name_row"
    const val SUBMIT = "sign_in_submit"
    const val ERROR = "sign_in_error"
    const val SESSION_ENDED = "sign_in_session_ended"
    const val LOCAL_NETWORK = "sign_in_local_network"
}

@PkPreviews
@Composable
private fun SignInScreenPreview() = PkPreviewSurface {
    SignInScreen(
        SignInUiState(address = "https://pets.example.com", phoneName = "Pixel 8", password = "secret"),
        SignInActions(),
    )
}
