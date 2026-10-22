package dev.kyralabs.pingogram.feature.profile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TwoRowsTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.rememberMorphShape
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The profile's header (`docs/DESIGN_SYSTEM.md` §3.3, §6 "Profile header"): Material's large flexible top app bar,
 * built from its parts like the settings pages' medium one ([TwoRowsTopAppBar], whose title slot says which row it
 * fills), so the name takes `displaySmallEmphasized` on its expanded row and `titleLargeEmphasized` collapsed. The
 * expanded row holds the avatar above the name, centered, as Google Contacts' contact page does, and the status
 * ("online" in `primary`, "last seen …", "bot") with the account's labels below it; the bar is that much taller than
 * the large flexible bar's own expanded height, and grows with the font scale. [scrollBehavior]
 * (`exitUntilCollapsedScrollBehavior`) collapses it as the page scrolls, to the name alone beside back.
 *
 * The avatar is a circle while the bar is expanded and morphs into a `MaterialShapes.Cookie9Sided` as it collapses
 * past half way, and back, on the motion scheme's default spatial spring (`rememberMorphShape`, which snaps with
 * reduced motion); the morph progress is read in the draw phase, and the collapse only flips a derived boolean.
 *
 * Transparent on the page's color. Without [onBack] there is no back button.
 */
@Composable
internal fun ProfileTopBar(
    header: ProfileHeader?,
    scrollBehavior: TopAppBarScrollBehavior,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val collapsing by remember(scrollBehavior) { derivedStateOf { scrollBehavior.state.collapsedFraction > HALF } }
    TwoRowsTopAppBar(
        title = { expanded -> header?.let { ProfileTitle(it, expanded, collapsing) } },
        modifier = modifier,
        subtitle = { expanded -> header?.let { if (expanded) ProfileSubtitle(it) } },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        painter = painterResource(UiR.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.profile_back),
                    )
                }
            }
        },
        titleHorizontalAlignment = Alignment.CenterHorizontally,
        collapsedHeight = TopAppBarDefaults.LargeAppBarCollapsedHeight,
        expandedHeight = expandedHeight(),
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
        scrollBehavior = scrollBehavior,
    )
}

/**
 * The bar's expanded height: its collapsed row, then the avatar, the name's line, the status's line and the room the
 * large flexible bar leaves around its title, in the current font scale.
 */
@Composable
private fun expandedHeight(): Dp {
    val typography = MaterialTheme.typography
    val spacing = MaterialTheme.spacing
    val lines =
        with(LocalDensity.current) {
            typography.displaySmallEmphasized.lineHeight.toDp() + typography.bodyLarge.lineHeight.toDp()
        }
    return TopAppBarDefaults.LargeAppBarCollapsedHeight + MaterialTheme.sizes.avatarProfile + lines +
        spacing.medium + spacing.extraSmall + spacing.large
}

/**
 * The name, a heading: on the [expanded] row under the avatar (whose shape morphs while [collapsing]), in
 * `displaySmallEmphasized`, up to two lines and smaller rather than cut off at large font scales; collapsed in
 * `titleLargeEmphasized` on one line. A verified badge follows it in `primary`.
 */
@Composable
private fun ProfileTitle(
    header: ProfileHeader,
    expanded: Boolean,
    collapsing: Boolean,
) {
    val name = header.name.resolve()
    val typography = MaterialTheme.typography
    val style = if (expanded) typography.displaySmallEmphasized else typography.titleLargeEmphasized
    val verified = stringResource(R.string.profile_badge_verified)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (expanded) {
            ProfileAvatar(header, name, collapsing)
            Spacer(Modifier.size(MaterialTheme.spacing.medium))
        }
        Row(
            modifier =
                Modifier.semantics(mergeDescendants = true) {
                    heading()
                    if (header.isVerified) contentDescription = "$name, $verified"
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            Text(
                text = name,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = if (expanded) EXPANDED_TITLE_MAX_LINES else 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                autoSize = if (expanded) TextAutoSize.StepBased(maxFontSize = style.fontSize) else null,
                style = style.userContent,
            )
            if (header.isVerified) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_verified_filled),
                    contentDescription = null,
                    modifier = Modifier.size(MaterialTheme.sizes.inlineIcon),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** The avatar at [MaterialTheme.sizes]' `avatarProfile`, clipped to the shape that morphs while [collapsing]. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProfileAvatar(
    header: ProfileHeader,
    name: String,
    collapsing: Boolean,
) {
    val shape = rememberMorphShape(MaterialShapes.Circle, MaterialShapes.Cookie9Sided, morphed = collapsing)
    Avatar(
        photo = header.photo,
        name = name,
        id = header.userId,
        size = MaterialTheme.sizes.avatarProfile,
        modifier = Modifier.clip(shape),
        placeholderIcon = header.placeholderIcon,
        contentDescription = stringResource(R.string.profile_photo_description, name),
    )
}

/**
 * The status under the name ("online" in `primary`, anything else in `onSurfaceVariant`) in `bodyLarge`, then the
 * account's labels: "Premium" on `tertiaryContainer` (a status, never an offer), "Scam" or "Fake" on `errorContainer`.
 */
@Composable
private fun ProfileSubtitle(header: ProfileHeader) {
    val colors = MaterialTheme.colorScheme
    Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        header.status?.let { status ->
            Text(
                text = status.resolve(),
                color = if (header.isOnline) colors.primary else colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        if (header.isPremium) {
            ProfileLabel(
                stringResource(R.string.profile_badge_premium),
                colors.tertiaryContainer,
                colors.onTertiaryContainer,
            )
        }
        when (header.warning) {
            ProfileWarning.Scam -> {
                ProfileLabel(
                    stringResource(R.string.profile_badge_scam),
                    colors.errorContainer,
                    colors.onErrorContainer,
                )
            }

            ProfileWarning.Fake -> {
                ProfileLabel(
                    stringResource(R.string.profile_badge_fake),
                    colors.errorContainer,
                    colors.onErrorContainer,
                )
            }

            null -> {}
        }
    }
}

/** A label after the status: `labelMediumEmphasized` on a pill of [container]. */
@Composable
internal fun ProfileLabel(
    text: String,
    container: Color,
    content: Color,
) {
    val spacing = MaterialTheme.spacing
    Text(
        text = text,
        modifier =
            Modifier
                .background(container, CircleShape)
                .padding(horizontal = spacing.small, vertical = spacing.extraSmall),
        color = content,
        maxLines = 1,
        style = MaterialTheme.typography.labelMediumEmphasized,
    )
}

/** The lines the name may take on the bar's expanded row, as Material's flexible bars wrap a title. */
private const val EXPANDED_TITLE_MAX_LINES = 2

/** How far the bar collapses before the avatar morphs. */
private const val HALF = 0.5f
