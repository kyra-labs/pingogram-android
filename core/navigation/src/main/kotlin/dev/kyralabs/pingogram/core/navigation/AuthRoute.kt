package dev.kyralabs.pingogram.core.navigation

import kotlinx.serialization.Serializable

/**
 * The login flow (`feature:auth`): one destination that shows whichever step TDLib is at, from the welcome screen to
 * the 2-step verification password. Shown until the account is authorized, and again after logging out.
 */
@Serializable
data object AuthRoute
