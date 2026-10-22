package dev.kyralabs.pingogram.link

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.LinkTarget
import dev.kyralabs.pingogram.core.domain.model.isWebLink
import dev.kyralabs.pingogram.core.domain.repository.LinkRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Opens Telegram links: from another app (`MainActivity`'s intents, through `OpenLinkEffect`) and tapped in the app
 * (`LinkUriHandler`). The app shell (`PingogramApp`) shows [isResolving] and carries out [effects].
 */
@Stable
interface LinkDispatcher {
    /** Whether a link is being looked up; the shell says so after a moment, with a way to [cancel]. */
    val isResolving: StateFlow<Boolean>

    /** What came of each link, once: a screen to open, or a snackbar. */
    val effects: Flow<LinkEffect>

    /** Opens [link], in place of one still being looked up; [fromOutside] if another app handed it over. */
    fun open(
        link: String,
        fromOutside: Boolean = false,
    )

    /** Stops looking up the link being opened: nothing opens. */
    fun cancel()
}

/**
 * [LinkDispatcher] on [LinkRepository] (`docs/adr/0013-deep-links.md`): resolves one link at a time, a new one
 * replacing the one before, and turns the target into a screen to open or a notice ([LinkEffect]). A chat with a
 * suggested text gets it as its draft first, unless it has one ([LinkRepository.prefillDraft]); nothing is ever sent,
 * joined or confirmed here. Scoped to the activity, so a link being looked up survives rotation; nothing to restore
 * after process death, as the link is gone with the screen that tapped it (an intent is not handled again then).
 */
@HiltViewModel
class LinkViewModel
    @Inject
    constructor(
        private val linkRepository: LinkRepository,
    ) : ViewModel(),
        LinkDispatcher {
        private val resolving = MutableStateFlow(false)
        override val isResolving: StateFlow<Boolean> = resolving.asStateFlow()

        private val effectChannel = Channel<LinkEffect>(Channel.BUFFERED)
        override val effects: Flow<LinkEffect> = effectChannel.receiveAsFlow()

        private var job: Job? = null

        override fun open(
            link: String,
            fromOutside: Boolean,
        ) {
            job?.cancel()
            resolving.value = true
            job =
                viewModelScope.launch {
                    val effect = effectOf(link, fromOutside)
                    resolving.value = false
                    effectChannel.send(effect)
                }
        }

        override fun cancel() {
            job?.cancel()
            job = null
            resolving.value = false
        }

        private suspend fun effectOf(
            link: String,
            fromOutside: Boolean,
        ): LinkEffect {
            val target =
                when (val resolved = linkRepository.resolve(link)) {
                    is Outcome.Success -> resolved.value
                    is Outcome.Failure -> return LinkEffect.Notice(LinkNotice.Failed(resolved.error))
                }
            return when {
                target is LinkTarget.NotFound -> {
                    LinkEffect.Notice(LinkNotice.NotFound(target.subject))
                }

                target == LinkTarget.LoginCode -> {
                    LinkEffect.Notice(LinkNotice.LoginCode)
                }

                target is LinkTarget.Unsupported -> {
                    LinkEffect.Notice(LinkNotice.Unsupported(target.url.takeIf(::isWebLink)))
                }

                !target.hasScreen() -> {
                    LinkEffect.Notice(LinkNotice.NotYet)
                }

                else -> {
                    // The chat opens either way; without the draft, the composer stays as it was.
                    if (target is LinkTarget.Chat) {
                        target.draftText?.let { linkRepository.prefillDraft(target.chatId, it) }
                    }
                    LinkEffect.Open(target, fromOutside)
                }
            }
        }
    }
