package com.nihaht.lockapp.ui.components

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Envoltorio de BiometricPrompt. Solo acepta biometría fuerte (huella en Samsung). */
object BiometricHelper {

    private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG

    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    /** Debe crearse en onCreate de la Activity. */
    fun createPrompt(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onFinished: () -> Unit = {},
    ): BiometricPrompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onFinished()
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onFinished()
            }
        },
    )

    fun promptInfo(title: String): BiometricPrompt.PromptInfo =
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle("Usa tu huella para continuar")
            .setNegativeButtonText("Usar PIN")
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
}
