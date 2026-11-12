package dev.kyralabs.pingogram.feature.chat.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.ui.component.CheckSwitch
import dev.kyralabs.pingogram.core.ui.component.GrowingVisibility
import dev.kyralabs.pingogram.core.ui.component.StateCrossfade
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The result on `surfaceContainerHigh` with `large` corners: a `LinearWavyProgressIndicator` while Telegram's AI
 * works, the text it gave ([AiResultText]), or why it failed in `error`, a polite live region; nothing before.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AiResultCard(result: AiResult) {
    // The card grows in with the first result and shrinks away when a changed choice drops it, showing the last one.
    val last = remember { arrayOfNulls<AiResult>(1) }
    if (result != AiResult.Idle) last[0] = result
    GrowingVisibility(visible = result != AiResult.Idle) {
        last[0]?.let { ResultCard(it) }
    }
}

/** [AiResultCard]'s card: its states crossfade in place, the card's height on the scheme's spring. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ResultCard(result: AiResult) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            Modifier
                .padding(MaterialTheme.spacing.medium)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            StateCrossfade(targetState = result, contentKey = { it::class }, label = "aiResult") { shown ->
                ResultContent(shown)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ResultContent(result: AiResult) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        when (result) {
            AiResult.Working -> {
                Text(
                    text = stringResource(R.string.ai_editor_working),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearWavyProgressIndicator(Modifier.fillMaxWidth())
            }

            is AiResult.Done -> {
                AiResultText(result)
            }

            is AiResult.Failed -> {
                Text(
                    text = errorText(result.error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            AiResult.Idle -> {
                Unit
            }
        }
    }
}
