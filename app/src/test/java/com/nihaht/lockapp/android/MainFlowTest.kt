package com.nihaht.lockapp.android

import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nihaht.lockapp.MainActivity
import com.nihaht.lockapp.diagnostics.ErrorLog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Recorre la app como un usuario: primer uso, lista, diagnóstico y ajustes. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MainFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

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
    fun `primer uso, lista, diagnostico y ajustes sin errores`() {
        ErrorLog.clear()

        // 1. Crear el PIN maestro (con un intento de confirmación equivocado).
        waitText("Bienvenido a LockApp")
        typePin("1234")
        waitText("Confirma el PIN")
        typePin("9999")
        waitText("no coinciden")
        typePin("1234")
        waitText("Confirma el PIN")
        typePin("1234")

        // 2. Asistente de permisos (la accesibilidad está desactivada en la prueba).
        waitText("Configura LockApp")
        compose.onNodeWithText("Continuar sin activar (no bloqueará)").performScrollTo().performClick()

        // 3. Lista de apps con el aviso de que no se está bloqueando.
        waitText("Buscar app")
        waitText("servicio de accesibilidad está desactivado")

        // 4. Diagnóstico: detecta el problema y "Reiniciar protección" no rompe nada.
        compose.onNodeWithContentDescription("Diagnóstico").performClick()
        waitText("Servicio de accesibilidad")
        waitText("Hay problemas que revisar")
        compose.onNodeWithText("Reiniciar protección").performScrollTo().performClick()
        waitText("Reiniciar protección")

        // 5. Dos toques muy rápidos en "Atrás" deben dejar la lista, no una pantalla en blanco.
        compose.onNodeWithContentDescription("Atrás").performTouchInput {
            click()
            advanceEventTime(30)
            click()
        }
        waitText("Buscar app")

        // 6. Ajustes: nuevas opciones visibles.
        compose.onNodeWithContentDescription("Ajustes").performClick()
        waitText("Proteger Recientes")
        compose.onNodeWithText("Diagnóstico").performScrollTo()
        compose.onNodeWithContentDescription("Atrás").performClick()
        waitText("Buscar app")

        assertEquals("Errores registrados: " + ErrorLog.read(), 0, ErrorLog.count())
    }
}
