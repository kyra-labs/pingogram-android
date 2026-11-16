package dev.kyralabs.pingogram.tile

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.net.toUri
import dev.kyralabs.pingogram.MainActivity
import dev.kyralabs.pingogram.R

/**
 * The New message tile of Quick Settings (`docs/DESIGN_SYSTEM.md` §13.13, board 33): a tap opens the New chat screen,
 * after unlocking the phone where it is locked, through the Telegram link the app shortcut sends (`tg://new`), so it
 * waits for the login and the passcode lock as any link does.
 */
class NewMessageTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        tile.state = Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_new_message)
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun(::open) else open()
    }

    private fun open() {
        openActivity(
            Intent(Intent.ACTION_VIEW, NEW_CHAT_LINK.toUri(), this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private companion object {
        const val NEW_CHAT_LINK = "tg://new"
    }
}
