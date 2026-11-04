package com.nihaht.lockapp.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nihaht.lockapp.diagnostics.ErrorLog
import com.nihaht.lockapp.service.KeepAliveService
import com.nihaht.lockapp.service.Notifications
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class KeepAliveServiceTest {

    @Test
    fun `se pone en primer plano nada mas crearse`() {
        // Si no lo hace a tiempo, Android cierra la app entera (el fallo que se vio en el teléfono).
        val service = Robolectric.buildService(KeepAliveService::class.java).create().get()
        val shadow = shadowOf(service)
        assertEquals(Notifications.SERVICE_ID, shadow.lastForegroundNotificationId)
        assertNotNull(shadow.lastForegroundNotification)
    }

    @Test
    fun `arranca, comprueba y se detiene sin registrar errores`() {
        ErrorLog.clear()
        val controller = Robolectric.buildService(KeepAliveService::class.java).create().startCommand(0, 1)
        assertNotNull(shadowOf(controller.get()).lastForegroundNotification)
        controller.startCommand(0, 2)
        controller.destroy()
        assertEquals("Errores: " + ErrorLog.read(), 0, ErrorLog.count())
    }
}
