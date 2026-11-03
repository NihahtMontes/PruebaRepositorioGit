package com.nihaht.lockapp.lock

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nihaht.lockapp.ui.components.BiometricHelper
import com.nihaht.lockapp.ui.theme.LockAppTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Pantalla que se muestra encima de una app bloqueada. Vive en su propia
 * tarea, excluida de recientes y con FLAG_SECURE (sin capturas ni miniaturas).
 */
class LockActivity : FragmentActivity() {

    private val viewModel: LockViewModel by viewModels()
    private lateinit var biometricPrompt: BiometricPrompt
    private var biometricShowing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()

        biometricPrompt = BiometricHelper.createPrompt(
            activity = this,
            onSuccess = { viewModel.onBiometricSuccess() },
            onFinished = { biometricShowing = false },
        )

        // Atrás nunca vuelve a la app bloqueada: lleva a la pantalla de inicio.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })

        if (!handleIntent(intent)) return

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.map { it.unlocked }.distinctUntilChanged().collect { unlocked ->
                    if (unlocked) finish()
                }
            }
        }

        setContent {
            LockAppTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val entry by viewModel.entry.state.collectAsStateWithLifecycle()

                // Lanza la huella automáticamente una vez por app, si está permitida.
                LaunchedEffect(state.packageName, state.ready, state.biometricAvailable) {
                    if (state.ready && state.biometricAvailable && entry.lockoutRemainingMs == 0L) {
                        showBiometric()
                    }
                }

                LockScreen(
                    state = state,
                    entry = entry,
                    onDigit = viewModel.entry::onDigit,
                    onDelete = viewModel.entry::onDelete,
                    onSubmit = viewModel.entry::onSubmit,
                    onBiometric = ::showBiometric,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?): Boolean {
        val packageName = intent?.getStringExtra(EXTRA_PACKAGE)
        if (packageName.isNullOrEmpty()) {
            finish()
            return false
        }
        viewModel.load(packageName)
        return true
    }

    private fun showBiometric() {
        val state = viewModel.state.value
        if (!state.biometricAvailable || biometricShowing || isFinishing) return
        biometricShowing = true
        biometricPrompt.authenticate(BiometricHelper.promptInfo("Desbloquear ${state.appLabel}"))
    }

    private fun goHome() {
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(home)
        finish()
    }

    companion object {
        private const val EXTRA_PACKAGE = "com.nihaht.lockapp.extra.PACKAGE"

        fun intent(context: Context, packageName: String): Intent =
            Intent(context, LockActivity::class.java)
                .putExtra(EXTRA_PACKAGE, packageName)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
                )
    }
}
