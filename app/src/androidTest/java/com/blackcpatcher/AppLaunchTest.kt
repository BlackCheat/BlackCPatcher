package com.blackcpatcher

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLaunchTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testAppHeaderAndDashboardLaunch() {
        composeTestRule.onNodeWithText("BlackCPatcher Studio").assertIsDisplayed()
        composeTestRule.onNodeWithText("1. APK Selection").assertIsDisplayed()
        composeTestRule.onNodeWithText("2. Build & Export Engine").assertIsDisplayed()
    }
}
