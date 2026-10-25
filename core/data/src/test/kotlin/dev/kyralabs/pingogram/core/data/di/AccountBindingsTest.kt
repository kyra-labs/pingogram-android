package dev.kyralabs.pingogram.core.data.di

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier

/**
 * An account repository is bound in three places (`docs/adr/0031-multiple-accounts.md`): [RepositoryModule] binds it in
 * the account's graph, [AccountRepositories] lets the graph hand it out, and [ActiveAccountModule] gives the active
 * account's to the app. A repository left out of one of them would compile and only be missed where it is injected.
 */
class AccountBindingsTest {
    private fun returnTypes(type: Class<*>) =
        type.declaredMethods
            .filter { !Modifier.isStatic(it.modifiers) || type == ActiveAccountModule::class.java }
            .filter { it.parameterCount <= 1 && !it.isSynthetic }
            .map { it.returnType.name }
            .filter { it != "void" }
            .toSortedSet()

    @Test
    fun `every repository the account graph binds, it hands out`() {
        assertEquals(returnTypes(RepositoryModule::class.java), returnTypes(AccountRepositories::class.java))
    }

    @Test
    fun `every repository the account graph hands out, the app gets from the active account`() {
        assertEquals(returnTypes(AccountRepositories::class.java), returnTypes(ActiveAccountModule::class.java))
    }
}
