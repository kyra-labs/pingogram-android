package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.domain.repository.DemoRepository
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * [DemoRepository] in memory: [enter] and [leave] set [isDemo], and count how often each was called; [receiveCall]
 * records who was asked to call.
 */
class FakeDemoRepository(
    isDemo: Boolean = false,
) : DemoRepository {
    override val isDemo = MutableStateFlow(isDemo)

    var entered = 0
        private set

    var left = 0
        private set

    override suspend fun enter() {
        entered++
        isDemo.value = true
    }

    override suspend fun leave() {
        left++
        isDemo.value = false
    }

    val callers = mutableListOf<Long>()

    override suspend fun receiveCall(userId: Long) {
        callers += userId
    }
}
