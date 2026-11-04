package com.nihaht.lockapp.android

import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nihaht.lockapp.LockApp
import com.nihaht.lockapp.lock.LockActivity
import com.nihaht.lockapp.security.PinHasher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class LockActivityTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app: LockApp = ApplicationProvider.getApplicationContext()
    private val pkg = "com.example.chat"

    @Before
    fun setUp() {
        runBlocking { app.container.lockedAppsRepository.lock(pkg, PinHasher.create("5678", 1_000), false) }
    }

    private fun waitText(text: String, timeoutMs: Long = 15_000) {
        compose.waitUntil(timeoutMs) {
            compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun typePin(pin: String) {
        pin.forEach { compose.onNodeWithText(it.toString()).performClick() }
        compose.onNodeWithContentDescription("Aceptar").performClick()
    }

    @Test
    fun `PIN incorrecto no abre y el correcto si`() {
        val scenario = ActivityScenario.launch<LockActivity>(LockActivity.intent(app, pkg))
        waitText("Introduce el PIN de esta app")
        typePin("0000")
        waitText("PIN incorrecto")
        assertEquals(Lifecycle.State.RESUMED, scenario.state)
        typePin("5678")
        compose.waitUntil(15_000) { scenario.state == Lifecycle.State.DESTROYED }
    }

    @Test
    fun `Atras lleva al inicio y nunca a la app bloqueada`() {
        val scenario = ActivityScenario.launch<LockActivity>(LockActivity.intent(app, pkg))
        waitText("Introduce el PIN de esta app")
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(15_000) { scenario.state == Lifecycle.State.DESTROYED }
        var home: Intent? = null
        while (true) {
            val next = shadowOf(app).nextStartedActivity ?: break
            if (next.hasCategory(Intent.CATEGORY_HOME)) home = next
        }
        assertEquals(Intent.ACTION_MAIN, home?.action)
    }
}
