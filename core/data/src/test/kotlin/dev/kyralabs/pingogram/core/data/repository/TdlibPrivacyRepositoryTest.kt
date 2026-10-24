package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.NewChatPrivacy
import dev.kyralabs.pingogram.core.domain.model.PrivacyChoice
import dev.kyralabs.pingogram.core.domain.model.PrivacyExceptions
import dev.kyralabs.pingogram.core.domain.model.PrivacyRules
import dev.kyralabs.pingogram.core.domain.model.PrivacyRulesChange
import dev.kyralabs.pingogram.core.domain.model.PrivacySetting
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

@OptIn(ExperimentalCoroutinesApi::class)
class TdlibPrivacyRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibPrivacyRepository(client)

    private fun rules(vararg rules: TdApi.UserPrivacySettingRule) = TdApi.UserPrivacySettingRules(arrayOf(*rules))

    private fun answerRules(vararg rules: TdApi.UserPrivacySettingRule) =
        client.on(TdApi.GetUserPrivacySettingRules::class, TdResult.Success(rules(*rules)))

    private fun answerSet(result: TdResult<TdApi.Ok> = TdResult.Success(TdApi.Ok())) =
        client.on(TdApi.SetUserPrivacySettingRules::class, result)

    private suspend fun rulesOf(vararg rules: TdApi.UserPrivacySettingRule): PrivacyRules {
        answerRules(*rules)
        return (repository.rules(PrivacySetting.LastSeen) as Outcome.Success).value
    }

    /** The rules last sent with setUserPrivacySettingRules, as their classes and ids. */
    private fun sentRules(): List<String> =
        client.sentOf<TdApi.SetUserPrivacySettingRules>().last().rules.rules.map { rule ->
            val ids =
                when (rule) {
                    is TdApi.UserPrivacySettingRuleAllowUsers -> rule.userIds.toList()
                    is TdApi.UserPrivacySettingRuleRestrictUsers -> rule.userIds.toList()
                    is TdApi.UserPrivacySettingRuleAllowChatMembers -> rule.chatIds.toList()
                    is TdApi.UserPrivacySettingRuleRestrictChatMembers -> rule.chatIds.toList()
                    else -> emptyList()
                }
            rule.javaClass.simpleName.removePrefix("UserPrivacySettingRule") + if (ids.isEmpty()) "" else ids.toString()
        }

    // Reading

    @ParameterizedTest
    @EnumSource(PrivacySetting::class)
    fun `asks TDLib for the rules of each setting`(setting: PrivacySetting) =
        runTest {
            answerRules(TdApi.UserPrivacySettingRuleAllowAll())

            repository.rules(setting)

            val expected =
                when (setting) {
                    PrivacySetting.PhoneNumber -> {
                        TdApi.UserPrivacySettingShowPhoneNumber::class.java
                    }

                    PrivacySetting.LastSeen -> {
                        TdApi.UserPrivacySettingShowStatus::class.java
                    }

                    PrivacySetting.ProfilePhoto -> {
                        TdApi.UserPrivacySettingShowProfilePhoto::class.java
                    }

                    PrivacySetting.Bio -> {
                        TdApi.UserPrivacySettingShowBio::class.java
                    }

                    PrivacySetting.Birthdate -> {
                        TdApi.UserPrivacySettingShowBirthdate::class.java
                    }

                    PrivacySetting.ForwardedMessages -> {
                        TdApi.UserPrivacySettingShowLinkInForwardedMessages::class.java
                    }

                    PrivacySetting.Calls -> {
                        TdApi.UserPrivacySettingAllowCalls::class.java
                    }

                    PrivacySetting.ChatInvites -> {
                        TdApi.UserPrivacySettingAllowChatInvites::class.java
                    }

                    PrivacySetting.VoiceMessages -> {
                        TdApi.UserPrivacySettingAllowPrivateVoiceAndVideoNoteMessages::class.java
                    }
                }
            assertInstanceOf(expected, client.sentOf<TdApi.GetUserPrivacySettingRules>().single().setting)
        }

    @Test
    fun `allow all is everybody, allow contacts is my contacts`() =
        runTest {
            assertEquals(PrivacyRules(PrivacyChoice.Everybody), rulesOf(TdApi.UserPrivacySettingRuleAllowAll()))
            assertEquals(PrivacyRules(PrivacyChoice.Contacts), rulesOf(TdApi.UserPrivacySettingRuleAllowContacts()))
        }

    @Test
    fun `no main rule, or restrict all, is nobody`() =
        runTest {
            assertEquals(PrivacyRules(PrivacyChoice.Nobody), rulesOf())
            assertEquals(PrivacyRules(PrivacyChoice.Nobody), rulesOf(TdApi.UserPrivacySettingRuleRestrictAll()))
        }

    @Test
    fun `the first main rule decides`() =
        runTest {
            assertEquals(
                PrivacyChoice.Contacts,
                rulesOf(TdApi.UserPrivacySettingRuleAllowContacts(), TdApi.UserPrivacySettingRuleAllowAll()).choice,
            )
        }

    @Test
    fun `counts the users and groups always and never allowed`() =
        runTest {
            val rules =
                rulesOf(
                    TdApi.UserPrivacySettingRuleAllowUsers(longArrayOf(1, 2)),
                    TdApi.UserPrivacySettingRuleAllowChatMembers(longArrayOf(-100)),
                    TdApi.UserPrivacySettingRuleRestrictUsers(longArrayOf(3, 4, 5)),
                    TdApi.UserPrivacySettingRuleRestrictChatMembers(longArrayOf(-200, -300)),
                    TdApi.UserPrivacySettingRuleAllowContacts(),
                )

            assertEquals(
                PrivacyRules(
                    PrivacyChoice.Contacts,
                    PrivacyExceptions(
                        allowedUserCount = 2,
                        allowedChatCount = 1,
                        restrictedUserCount = 3,
                        restrictedChatCount = 2,
                    ),
                ),
                rules,
            )
        }

    @Test
    fun `premium subscribers allowed are an exception, bots are not`() =
        runTest {
            val rules =
                rulesOf(
                    TdApi.UserPrivacySettingRuleAllowPremiumUsers(),
                    TdApi.UserPrivacySettingRuleAllowBots(),
                    TdApi.UserPrivacySettingRuleAllowContacts(),
                )

            assertEquals(PrivacyRules(PrivacyChoice.Contacts, PrivacyExceptions(allowsPremiumUsers = true)), rules)
        }

    @Test
    fun `reading maps errors`() =
        runTest {
            client.on(
                TdApi.GetUserPrivacySettingRules::class,
                TdResult.Failure(TdError(429, "Too Many Requests: retry after 8")),
            )

            assertEquals(Outcome.Failure(DomainError.FloodWait(8)), repository.rules(PrivacySetting.Calls))
        }

    @Test
    fun `reading without an answer in time fails with a network error`() =
        runTest {
            client.on(TdApi.GetUserPrivacySettingRules::class) {
                CompletableDeferred<TdResult<TdApi.UserPrivacySettingRules>>().await()
            }

            val result = async { repository.rules(PrivacySetting.Calls) }
            advanceTimeBy(REQUEST_TIMEOUT)
            runCurrent()

            assertEquals(Outcome.Failure(DomainError.Network), result.await())
        }

    // Changes

    @Test
    fun `reports the changes TDLib sends for the settings it shows`() =
        runTest {
            val changes = mutableListOf<PrivacyRulesChange>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.changes.toList(changes) }

            client.emit(
                TdApi.UpdateUserPrivacySettingRules(
                    TdApi.UserPrivacySettingShowProfilePhoto(),
                    rules(TdApi.UserPrivacySettingRuleAllowContacts()),
                ),
            )
            // Not shown by Pingogram.
            client.emit(
                TdApi.UpdateUserPrivacySettingRules(
                    TdApi.UserPrivacySettingAutosaveGifts(),
                    rules(TdApi.UserPrivacySettingRuleAllowAll()),
                ),
            )

            assertEquals(
                listOf(PrivacyRulesChange(PrivacySetting.ProfilePhoto, PrivacyRules(PrivacyChoice.Contacts))),
                changes,
            )
        }

    // Changing the main choice

    @Test
    fun `changing the choice keeps every exception in its order, before the new main rule`() =
        runTest {
            answerRules(
                TdApi.UserPrivacySettingRuleRestrictUsers(longArrayOf(3)),
                TdApi.UserPrivacySettingRuleAllowAll(),
                TdApi.UserPrivacySettingRuleAllowUsers(longArrayOf(1, 2)),
                TdApi.UserPrivacySettingRuleRestrictChatMembers(longArrayOf(-200)),
                TdApi.UserPrivacySettingRuleAllowBots(),
            )
            answerSet()

            val result = repository.setChoice(PrivacySetting.PhoneNumber, PrivacyChoice.Contacts)

            assertEquals(
                listOf(
                    "RestrictUsers[3]",
                    "AllowUsers[1, 2]",
                    "RestrictChatMembers[-200]",
                    "AllowBots",
                    "AllowContacts",
                ),
                sentRules(),
            )
            assertEquals(
                Outcome.Success(
                    PrivacyRules(
                        PrivacyChoice.Contacts,
                        PrivacyExceptions(allowedUserCount = 2, restrictedUserCount = 1, restrictedChatCount = 1),
                    ),
                ),
                result,
            )
        }

    @Test
    fun `the change goes to the setting it is for`() =
        runTest {
            answerRules(TdApi.UserPrivacySettingRuleAllowAll())
            answerSet()

            repository.setChoice(PrivacySetting.ChatInvites, PrivacyChoice.Contacts)

            assertInstanceOf(
                TdApi.UserPrivacySettingAllowChatInvites::class.java,
                client.sentOf<TdApi.GetUserPrivacySettingRules>().single().setting,
            )
            assertInstanceOf(
                TdApi.UserPrivacySettingAllowChatInvites::class.java,
                client.sentOf<TdApi.SetUserPrivacySettingRules>().single().setting,
            )
        }

    @Test
    fun `everybody replaces every main rule with allow all`() =
        runTest {
            answerRules(
                TdApi.UserPrivacySettingRuleAllowContacts(),
                TdApi.UserPrivacySettingRuleRestrictContacts(),
                TdApi.UserPrivacySettingRuleRestrictAll(),
            )
            answerSet()

            repository.setChoice(PrivacySetting.LastSeen, PrivacyChoice.Everybody)

            assertEquals(listOf("AllowAll"), sentRules())
        }

    @Test
    fun `nobody needs no main rule`() =
        runTest {
            answerRules(
                TdApi.UserPrivacySettingRuleAllowUsers(longArrayOf(7)),
                TdApi.UserPrivacySettingRuleAllowAll(),
            )
            answerSet()

            val result = repository.setChoice(PrivacySetting.Calls, PrivacyChoice.Nobody)

            assertEquals(listOf("AllowUsers[7]"), sentRules())
            assertEquals(
                Outcome.Success(PrivacyRules(PrivacyChoice.Nobody, PrivacyExceptions(allowedUserCount = 1))),
                result,
            )
        }

    @Test
    fun `nothing is changed when the rules cannot be read`() =
        runTest {
            client.on(TdApi.GetUserPrivacySettingRules::class, TdResult.Failure(TdError(500, "Network is unreachable")))
            answerSet()

            val result = repository.setChoice(PrivacySetting.Calls, PrivacyChoice.Nobody)

            assertEquals(Outcome.Failure(DomainError.Network), result)
            assertTrue(client.sentOf<TdApi.SetUserPrivacySettingRules>().isEmpty())
        }

    @Test
    fun `limiting voice messages without premium fails with premium required`() =
        runTest {
            answerRules(TdApi.UserPrivacySettingRuleAllowAll())
            answerSet(TdResult.Failure(TdError(403, "PREMIUM_ACCOUNT_REQUIRED")))

            assertEquals(
                Outcome.Failure(DomainError.PremiumRequired),
                repository.setChoice(PrivacySetting.VoiceMessages, PrivacyChoice.Contacts),
            )
        }

    @Test
    fun `the rules TDLib answered with are left as they were`() =
        runTest {
            val answered = rules(TdApi.UserPrivacySettingRuleAllowAll())
            client.on(TdApi.GetUserPrivacySettingRules::class, TdResult.Success(answered))
            answerSet()

            repository.setChoice(PrivacySetting.Calls, PrivacyChoice.Contacts)

            assertArrayEquals(
                arrayOf<Any>(TdApi.UserPrivacySettingRuleAllowAll::class.java),
                answered.rules.map { it.javaClass }.toTypedArray(),
            )
        }

    // Read time and new chats

    @Test
    fun `reads and sets whether the read time shows`() =
        runTest {
            client.on(TdApi.GetReadDatePrivacySettings::class, TdResult.Success(TdApi.ReadDatePrivacySettings(true)))
            client.on(TdApi.SetReadDatePrivacySettings::class, TdResult.Success(TdApi.Ok()))

            assertEquals(Outcome.Success(true), repository.showsReadDate())
            assertEquals(Outcome.Success(Unit), repository.setShowsReadDate(false))

            assertFalse(
                client
                    .sentOf<TdApi.SetReadDatePrivacySettings>()
                    .single()
                    .settings.showReadDate,
            )
        }

    @Test
    fun `new chats from non-contacts, whether they can be limited, and paid messages`() =
        runTest {
            client.on(
                TdApi.GetNewChatPrivacySettings::class,
                TdResult.Success(TdApi.NewChatPrivacySettings(true, 10)),
            )
            client.on(TdApi.GetOption::class, TdResult.Success(TdApi.OptionValueBoolean(true)))

            assertEquals(
                Outcome.Success(NewChatPrivacy(allowsNonContacts = true, paidMessageStars = 10, canRestrict = true)),
                repository.newChatPrivacy(),
            )
            assertEquals("can_set_new_chat_privacy_settings", client.sentOf<TdApi.GetOption>().single().name)
        }

    @Test
    fun `an option TDLib does not know means non-contacts cannot be limited`() =
        runTest {
            client.on(
                TdApi.GetNewChatPrivacySettings::class,
                TdResult.Success(TdApi.NewChatPrivacySettings(true, 0)),
            )
            client.on(TdApi.GetOption::class, TdResult.Success(TdApi.OptionValueEmpty()))

            assertEquals(Outcome.Success(NewChatPrivacy(allowsNonContacts = true)), repository.newChatPrivacy())
        }

    @Test
    fun `keeping non-contacts out drops their price, letting them in keeps it`() =
        runTest {
            client.on(
                TdApi.GetNewChatPrivacySettings::class,
                TdResult.Success(TdApi.NewChatPrivacySettings(true, 10)),
            )
            client.on(TdApi.SetNewChatPrivacySettings::class, TdResult.Success(TdApi.Ok()))

            repository.setAllowsNonContacts(false)
            repository.setAllowsNonContacts(true)

            val (out, inside) = client.sentOf<TdApi.SetNewChatPrivacySettings>().map { it.settings }
            assertEquals(false to 0L, out.allowNewChatsFromUnknownUsers to out.incomingPaidMessageStarCount)
            assertEquals(true to 10L, inside.allowNewChatsFromUnknownUsers to inside.incomingPaidMessageStarCount)
        }
}
