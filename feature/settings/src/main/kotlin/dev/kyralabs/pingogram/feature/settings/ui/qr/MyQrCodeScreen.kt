package dev.kyralabs.pingogram.feature.settings.ui.qr

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ProfileLink
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.qr.QrCode
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.numericContent
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.SettingsError
import dev.kyralabs.pingogram.feature.settings.ui.SettingsLoading
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPageScaffold
import java.text.NumberFormat
import kotlin.time.Duration.Companion.seconds
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The page of the user's own QR code (`MyQrCodeRoute`): hoists [MyQrCodeViewModel] and shares the link through
 * Android's share sheet. [onScan] opens the QR code scanner, [onEditProfile] Edit profile (to set a username), [onBack]
 * leaves the page.
 */
@Composable
internal fun MyQrCodeRoute(
    onScan: () -> Unit,
    onEditProfile: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MyQrCodeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    MyQrCodeScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onShare = { context.shareLink(it) },
        onScan = onScan,
        onEditProfile = onEditProfile,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * The user's own QR code (`docs/DESIGN_SYSTEM.md` §6 "Your QR code"), a settings page: the avatar (`avatarLarge`) and
 * the name in `titleLargeEmphasized`, the code of the link to their profile (`core:ui`'s [QrCode], dark on light in
 * every theme) on a `large`-rounded square inside the QR login's `MaterialShapes.Cookie12Sided` frame in
 * `primaryContainer`, the link below it (selectable, left to right); a temporary link (no username) says for how long
 * it works with "Set username". Then "Scan QR code" (`FilledTonalButton`) at the start and "Share" (default `Button`)
 * at the end.
 */
@Suppress("LongParameterList") // The page's state and where it leads.
@Composable
internal fun MyQrCodeScreen(
    state: MyQrCodeUiState,
    onEvent: (MyQrCodeEvent) -> Unit,
    onShare: (String) -> Unit,
    onScan: () -> Unit,
    onEditProfile: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPageScaffold(title = stringResource(R.string.settings_my_qr_title), onBack = onBack, modifier = modifier) {
        when (state) {
            MyQrCodeUiState.Loading -> {
                SettingsLoading(stringResource(R.string.settings_my_qr_loading))
            }

            is MyQrCodeUiState.Error -> {
                SettingsError(state.error, onRetry = { onEvent(MyQrCodeEvent.Retry) })
            }

            is MyQrCodeUiState.Content -> {
                MyQrCode(state, onShare, onScan, onEditProfile)
            }
        }
    }
}

@Composable
private fun MyQrCode(
    state: MyQrCodeUiState.Content,
    onShare: (String) -> Unit,
    onScan: () -> Unit,
    onEditProfile: () -> Unit,
) {
    val name = state.name.resolve()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
        Avatar(photo = state.photo, name = name, id = state.userId, size = MaterialTheme.sizes.avatarLarge)
        Text(
            text = name,
            modifier = Modifier.semantics { heading() },
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLargeEmphasized.userContent,
        )
        QrFrame(state.link.url, name)
        SelectionContainer {
            Text(
                text = state.link.url.removePrefix(HTTPS),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium.numericContent,
            )
        }
        if (state.link.isTemporary) TemporaryNote(state.link, onEditProfile)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(onClick = onScan, shapes = ButtonDefaults.shapes()) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_qr_code_scanner),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Text(
                    text = stringResource(R.string.settings_my_qr_scan),
                    modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Button(onClick = { onShare(state.link.url) }, shapes = ButtonDefaults.shapes()) {
                Text(
                    stringResource(R.string.settings_my_qr_share),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        }
    }
}

/** The code of [url] in the QR login's frame: a `Cookie12Sided` of `primaryContainer`, the code 60% of it. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun QrFrame(
    url: String,
    name: String,
) {
    Box(
        modifier =
            Modifier
                .widthIn(max = MaterialTheme.sizes.illustrationMaxSize)
                .fillMaxWidth(FRAME_WIDTH_FRACTION)
                .aspectRatio(1f)
                .background(MaterialTheme.colorScheme.primaryContainer, MaterialShapes.Cookie12Sided.toShape()),
        contentAlignment = Alignment.Center,
    ) {
        QrCode(
            text = url,
            contentDescription = stringResource(R.string.settings_my_qr_description, name),
            modifier = Modifier.fillMaxSize(CODE_FRACTION).clip(MaterialTheme.shapes.large),
        )
    }
}

/** For how long a temporary link works, and that a username makes it last, with "Set username". */
@Composable
private fun TemporaryNote(
    link: ProfileLink,
    onEditProfile: () -> Unit,
) {
    val numbers = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0])
    val duration = link.expiresInSeconds.seconds
    val validity =
        if (duration.inWholeHours >= 1) {
            val hours = duration.inWholeHours.toInt()
            pluralStringResource(R.plurals.settings_my_qr_temporary_hours, hours, numbers.format(hours))
        } else {
            val minutes = duration.inWholeMinutes.toInt().coerceAtLeast(1)
            pluralStringResource(R.plurals.settings_my_qr_temporary_minutes, minutes, numbers.format(minutes))
        }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = validity,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onEditProfile, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.settings_my_qr_set_username), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Hands [link] to Android's share sheet; nothing where no app takes it. */
private fun Context.shareLink(link: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link)
    try {
        startActivity(Intent.createChooser(send, getString(R.string.settings_my_qr_share)))
    } catch (_: ActivityNotFoundException) {
        // No app shares text: there is nothing to offer.
    }
}

private const val HTTPS = "https://"

/** How much of the page's width the frame takes on a phone, and how much of the frame the code takes. */
private const val FRAME_WIDTH_FRACTION = 0.8f
private const val CODE_FRACTION = 0.6f

@PingogramPreviews
@Composable
private fun MyQrCodeScreenPreview() {
    PingogramPreview {
        MyQrCodeScreen(
            MyQrCodeUiState.Content(1, UiText.Plain("Ada Lovelace"), null, ProfileLink("https://t.me/ada", 0)),
            {},
            {},
            {},
            {},
            {},
        )
    }
}

@PingogramPreviews
@Composable
private fun MyQrCodeScreenTemporaryPreview() {
    PingogramPreview {
        MyQrCodeScreen(
            MyQrCodeUiState.Content(
                1,
                UiText.Plain("Ada Lovelace"),
                null,
                ProfileLink("https://t.me/contact/AAAAAAAAAAAAAAAA", PREVIEW_LINK_SECONDS),
            ),
            {},
            {},
            {},
            {},
            {},
        )
    }
}

@PingogramPreviews
@Composable
private fun MyQrCodeScreenErrorPreview() {
    PingogramPreview { MyQrCodeScreen(MyQrCodeUiState.Error(DomainError.Network), {}, {}, {}, {}, {}) }
}

/** How long the previews' temporary link lasts: an hour. */
private const val PREVIEW_LINK_SECONDS = 3_600
