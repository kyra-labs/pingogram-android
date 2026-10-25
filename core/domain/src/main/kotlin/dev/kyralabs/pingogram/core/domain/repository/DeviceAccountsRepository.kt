package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.domain.model.DeviceAccount
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow

/**
 * The accounts on this device and the one the screens show (`docs/adr/0031-multiple-accounts.md`). Switching closes
 * the screens and opens them again for the other account; every account keeps running in the background.
 */
interface DeviceAccountsRepository {
    /**
     * Whether the user may add and switch accounts. False until every part of the app follows each account
     * (notifications, calls, push), so no screen offers it before then.
     */
    val isAvailable: Boolean

    /** The signed-in accounts, in the order they were added, then every change. */
    val accounts: Flow<ImmutableList<DeviceAccount>>

    /** Whether another account may be added: below the limit (`DeviceAccountLimits`), none being added. */
    val canAddAccount: Flow<Boolean>

    /** Whether an account being added is signing in while another is signed in, so the sign-in can be left. */
    val isAdding: Flow<Boolean>

    /** Shows [slot]'s account; nothing when it is the one shown or not on the device. */
    fun switchTo(slot: Int)

    /** Starts signing in another account, which becomes the one shown; nothing when [canAddAccount] is false. */
    fun addAccount()

    /** Leaves the sign-in of an account being added and shows the account shown before it. */
    fun cancelAdding()
}
