package com.nihaht.lockapp.android

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nihaht.lockapp.LockApp
import com.nihaht.lockapp.diagnostics.ErrorLog
import com.nihaht.lockapp.lock.LockActivity
import com.nihaht.lockapp.security.PinHasher
import com.nihaht.lockapp.service.AppLockAccessibilityService
import com.nihaht.lockapp.service.RecentsShield
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AppLockAccessibilityServiceTest {

    private val app: LockApp = ApplicationProvider.getApplicationContext()
    private val container get() = app.container
    private lateinit var service: AppLockAccessibilityService

    private val locked = "com.example.chat"
    private val other = "com.example.notes"
    private val launcher = "com.example.launcher"

    @Before
    fun setUp() {
        // Un launcher instalado, para reconocer la pantalla de inicio y Recientes.
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        shadowOf(app.packageManager).addResolveInfoForIntent(
            home,
            ResolveInfo().apply {
                activityInfo = ActivityInfo().apply {
                    packageName = launcher
                    name = "$launcher.HomeActivity"
                }
            },
        )
        runBlocking {
            container.lockedAppsRepository.lock(locked, PinHasher.create("5678", 1_000), false)
            container.settingsRepository.setPaused(false)
            container.settingsRepository.setProtectRecents(true)
        }
        waitUntil { container.lockedAppsState.value?.containsKey(locked) == true }
        waitUntil { container.settingsState.value?.let { !it.paused && it.protectRecents } == true }
        container.sessions.lockAll()
        ErrorLog.clear()

        service = Robolectric.buildService(AppLockAccessibilityService::class.java).create().get()
        AppLockAccessibilityService::class.java.getDeclaredMethod("onServiceConnected").apply {
            isAccessible = true
            invoke(service)
        }
        clearStartedActivities()
    }

    private fun open(packageName: String, className: String = "$packageName.MainActivity") {
        val event = AccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED).apply {
            this.packageName = packageName
            this.className = className
        }
        service.onAccessibilityEvent(event)
    }

    /** Paquete de la pantalla de bloqueo lanzada (o null si no se lanzó ninguna). */
    private fun lockShownFor(): String? {
        var found: String? = null
        while (true) {
            val intent = shadowOf(app).nextStartedActivity ?: break
            if (intent.component?.className == LockActivity::class.java.name) {
                found = intent.getStringExtra("com.nihaht.lockapp.extra.PACKAGE")
            }
        }
        return found
    }

    private fun clearStartedActivities() {
        while (shadowOf(app).nextStartedActivity != null) Unit
    }

    private fun shieldShowing(): Boolean {
        val field = AppLockAccessibilityService::class.java.getDeclaredField("shield").apply { isAccessible = true }
        return (field.get(service) as RecentsShield?)?.isShowing == true
    }

    @Test
    fun `abrir una app bloqueada muestra su pantalla de bloqueo`() {
        open(locked)
        assertEquals(locked, lockShownFor())
    }

    @Test
    fun `abrir una app sin bloquear no muestra nada`() {
        open(other)
        assertNull(lockShownFor())
    }

    @Test
    fun `dentro de la app desbloqueada no se vuelve a pedir`() {
        container.sessions.markUnlocked(locked)
        open(locked)
        open(locked, "$locked.ChatActivity")
        assertNull(lockShownFor())
    }

    @Test
    fun `teclado, barra de estado y llamadas no cuentan como salir de la app`() {
        container.sessions.markUnlocked(locked)
        open("com.android.systemui", "android.widget.FrameLayout")
        open("com.samsung.android.incallui")
        open(locked)
        assertNull(lockShownFor())
    }

    @Test
    fun `al volver tras ir al inicio se vuelve a pedir el PIN`() {
        container.sessions.markUnlocked(locked)
        open(launcher, "$launcher.HomeActivity")
        open(locked)
        assertEquals(locked, lockShownFor())
    }

    @Test
    fun `con los bloqueos en pausa no se bloquea nada`() {
        runBlocking { container.settingsRepository.setPaused(true) }
        waitUntil { container.settingsState.value?.paused == true }
        open(locked)
        assertNull(lockShownFor())
    }

    @Test
    fun `Recientes se tapa y se destapa al volver al inicio`() {
        open(launcher, "com.android.quickstep.RecentsActivity")
        assertTrue("Recientes debería quedar tapado", shieldShowing())
        assertEquals(1, container.status.recentsShieldShown)
        open(launcher, "$launcher.HomeActivity")
        assertFalse("Al volver al inicio se quita la capa", shieldShowing())
    }

    @Test
    fun `Recientes no se tapa si la opcion esta desactivada`() {
        runBlocking { container.settingsRepository.setProtectRecents(false) }
        waitUntil { container.settingsState.value?.protectRecents == false }
        open(launcher, "com.android.quickstep.RecentsActivity")
        assertFalse(shieldShowing())
    }

    @Test
    fun `abrir otra app quita la capa de Recientes`() {
        open(launcher, "com.android.quickstep.RecentsActivity")
        assertTrue(shieldShowing())
        open(other)
        assertFalse(shieldShowing())
    }

    @Test
    fun `al apagar la pantalla dentro de una app bloqueada se vuelve a bloquear`() {
        container.sessions.markUnlocked(locked)
        open(locked)
        assertNull(lockShownFor())
        app.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(locked, lockShownFor())
    }

    @Test
    fun `eventos raros no cierran el servicio`() {
        service.onAccessibilityEvent(null)
        service.onAccessibilityEvent(AccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED))
        service.onAccessibilityEvent(AccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED))
        assertNull(lockShownFor())
        assertEquals("Errores: " + ErrorLog.read(), 0, ErrorLog.count())
    }
}
