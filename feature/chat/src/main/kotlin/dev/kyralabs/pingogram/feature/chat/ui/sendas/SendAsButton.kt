package dev.kyralabs.pingogram.feature.chat.ui.sendas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.feature.chat.R

/**
 * The send-as button at the start of the composer, before the attach button (`REQUIREMENTS.md` FR-C5,
 * `docs/DESIGN_SYSTEM.md` §6 "Composer"): the avatar of who new messages go out as ([choice]), small
 * (`sizes.avatarExtraSmall`) in a small icon button whose state layer morphs in the Expressive shapes while pressed,
 * with a 48 dp touch target, centred on a one-line text field. It opens the send-as picker ([onClick]). Screen readers
 * hear who the user sends as ("Send as …"), as a button.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SendAsButton(
    choice: SendAsChoice,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = choice.name.resolve()
    val description = stringResource(R.string.send_as_button, name)
    Box(modifier.heightIn(min = IconButtonDefaults.mediumContainerSize().height), contentAlignment = Alignment.Center) {
        IconButton(
            onClick = onClick,
            modifier =
                Modifier
                    .size(IconButtonDefaults.smallContainerSize())
                    .semantics { contentDescription = description },
            shapes = IconButtonDefaults.shapes(),
        ) {
            Avatar(
                photo = choice.photo,
                name = name,
                id = choice.id,
                size = MaterialTheme.sizes.avatarExtraSmall,
            )
        }
    }
}

@PingogramPreviews
@Composable
private fun SendAsButtonPreview() {
    PingogramPreview {
        SendAsButton(SendAsChoice(id = -200, name = UiText.Plain("Pingogram News"), photo = null), onClick = {})
    }
}
