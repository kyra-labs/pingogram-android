package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Material's [ModalBottomSheet], which never rises behind the status bar: every sheet of the app goes through it.
 *
 * material3 1.5 lays a modal sheet out in an edge-to-edge window of its own and pads only its content by the status
 * bar, so a tall sheet's surface reached the top of the screen, the status bar's icons over it. Here the sheet lays
 * out below the status bar (and a display cutout at the top), so expanded it stops there, as it did before. Everything
 * else is Material's, unchanged.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PingogramBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberBottomSheetState(SheetValue.Hidden),
    properties: ModalBottomSheetProperties = ModalBottomSheetProperties(),
    content: @Composable ColumnScope.() -> Unit,
) = PingogramBottomSheet(
    onDismissRequest = onDismissRequest,
    topInset = WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
    modifier = modifier,
    sheetState = sheetState,
    properties = properties,
    content = content,
)

/** [PingogramBottomSheet] kept below [topInset]: the status bar in the app, a fixed inset in tests. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PingogramBottomSheet(
    onDismissRequest: () -> Unit,
    topInset: WindowInsets,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberBottomSheetState(SheetValue.Hidden),
    properties: ModalBottomSheetProperties = ModalBottomSheetProperties(),
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        // Outside Material's own modifiers: its anchors count from below the inset, so expanded the sheet stops there
        // and hidden it is still off screen; the content's top inset is consumed here.
        modifier = Modifier.windowInsetsPadding(topInset).then(modifier),
        sheetState = sheetState,
        properties = properties,
        content = content,
    )
}
