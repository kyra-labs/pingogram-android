package dev.kyralabs.pingogram.core.ui.component

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

/**
 * The properties of a Navigation `dialog` destination that only holds a `ModalBottomSheet` (the forward, share and
 * join sheets): an edge-to-edge window of the full width (`decorFitsSystemWindows = false`), so the sheet draws as it
 * does in place. Pair it with [NoDialogWindowDim].
 */
val SheetDialogProperties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)

/**
 * The dialog window holding this composition dims nothing behind it: the sheet's own scrim is the only one, so a
 * sheet in a dialog destination looks as it does in place (`docs/ARCHITECTURE.md`, Navigation).
 */
@Composable
fun NoDialogWindowDim() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect {
        window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window?.setDimAmount(0f)
    }
}
