package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.preview.SponsoredPreviewData
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** "About this ad": what it shows, reporting only where the ad can be reported, and the report step's reasons. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class SponsoredMessageSheetTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<ChatEvent>()

    private fun setSheet(sheet: SponsoredSheet) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) { SponsoredMessageSheet(sheet, onEvent = { events += it }) }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `it shows the advertiser and more about the ad, and reports it`() {
        val sheet = SponsoredPreviewData.info
        setSheet(sheet)

        composeRule.onNodeWithText(string(R.string.sponsored_about)).assertExists()
        composeRule.onNodeWithText(sheet.sponsorInfo).assertExists()
        composeRule.onNodeWithText(sheet.additionalInfo).assertExists()
        composeRule.onNodeWithText(string(R.string.sponsored_premium_note)).assertExists()
        composeRule.onNodeWithText(string(R.string.sponsored_report)).performClick()

        assertEquals(listOf<ChatEvent>(SponsoredEvent.Report), events)
    }

    @Test
    fun `an ad that cannot be reported offers no report`() {
        setSheet(SponsoredPreviewData.info.copy(canBeReported = false, isRecommended = true))

        composeRule.onNodeWithText(string(R.string.sponsored_about_recommended)).assertExists()
        composeRule.onNodeWithText(string(R.string.sponsored_report)).assertDoesNotExist()
    }

    @Test
    fun `the report step lists the reasons, and back returns`() {
        val sheet = SponsoredPreviewData.reportStep
        setSheet(sheet)

        composeRule.onNodeWithText(sheet.report!!.title).assertExists()
        composeRule.onNodeWithText(sheet.report.options[1]).performClick()
        composeRule.onNodeWithContentDescription(string(R.string.sponsored_report_back)).performClick()

        assertEquals(listOf(SponsoredEvent.ChooseReportOption(1), SponsoredEvent.BackToInfo), events)
    }

    @Test
    fun `the reasons wait while a report is on its way`() {
        val sheet = SponsoredPreviewData.reportStep.copy(isReporting = true)
        setSheet(sheet)

        composeRule.onNodeWithText(sheet.report!!.options.first()).assertIsNotEnabled()
    }

    private fun string(
        @StringRes id: Int,
    ): String = RuntimeEnvironment.getApplication().getString(id)
}
