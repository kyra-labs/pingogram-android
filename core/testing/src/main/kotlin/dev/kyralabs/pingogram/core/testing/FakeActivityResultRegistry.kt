package dev.kyralabs.pingogram.core.testing

import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat

/**
 * An [ActivityResultRegistry] that starts no other app: it records what was launched ([launches]), runs [beforeLaunch]
 * first (which may throw, as a launch Android refuses does), and answers a launch only when a test calls [answer].
 * Provide it to Compose through `LocalActivityResultRegistryOwner provides registry.owner`.
 */
class FakeActivityResultRegistry : ActivityResultRegistry() {
    /** One launch: its request code, contract and input. */
    data class Launch(
        val requestCode: Int,
        val contract: ActivityResultContract<*, *>,
        val input: Any?,
    )

    val launches = mutableListOf<Launch>()

    /** Runs before each launch is recorded; a launch it throws from is not recorded. */
    var beforeLaunch: (Launch) -> Unit = {}

    val owner: ActivityResultRegistryOwner =
        object : ActivityResultRegistryOwner {
            override val activityResultRegistry: ActivityResultRegistry = this@FakeActivityResultRegistry
        }

    override fun <I, O> onLaunch(
        requestCode: Int,
        contract: ActivityResultContract<I, O>,
        input: I,
        options: ActivityOptionsCompat?,
    ) {
        val launch = Launch(requestCode, contract, input)
        beforeLaunch(launch)
        launches += launch
    }

    /** The launches of contracts of type [C]. */
    inline fun <reified C : ActivityResultContract<*, *>> launchesOf(): List<Launch> =
        launches.filter { it.contract is C }

    /** Answers [launch] with [result], as the other app would. */
    fun <O> answer(
        launch: Launch,
        result: O,
    ) {
        dispatchResult(launch.requestCode, result)
    }
}
