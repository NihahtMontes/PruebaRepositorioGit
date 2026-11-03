package com.nihaht.lockapp

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nihaht.lockapp.service.KeepAliveService
import com.nihaht.lockapp.ui.applist.AppListViewModel
import com.nihaht.lockapp.ui.components.BiometricHelper
import com.nihaht.lockapp.ui.main.LockAppRoot
import com.nihaht.lockapp.ui.main.MainViewModel
import com.nihaht.lockapp.ui.theme.LockAppTheme
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/** Pantalla principal de LockApp. Hereda de FragmentActivity porque BiometricPrompt lo necesita. */
class MainActivity : FragmentActivity() {

    private val mainViewModel: MainViewModel by viewModels()
    private val appListViewModel: AppListViewModel by viewModels()
    private lateinit var biometricPrompt: BiometricPrompt
    private var biometricShowing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        biometricPrompt = BiometricHelper.createPrompt(
            activity = this,
            onSuccess = { mainViewModel.onMasterBiometricSuccess() },
            onFinished = { biometricShowing = false },
        )

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.message.filterNotNull().collect { text ->
                    Toast.makeText(this@MainActivity, text, Toast.LENGTH_SHORT).show()
                    mainViewModel.consumeMessage()
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                appListViewModel.error.filterNotNull().collect { text ->
                    Toast.makeText(this@MainActivity, text, Toast.LENGTH_SHORT).show()
                    appListViewModel.consumeError()
                }
            }
        }

        setContent {
            LockAppTheme {
                LockAppRoot(
                    mainViewModel = mainViewModel,
                    appListViewModel = appListViewModel,
                    onRequestMasterBiometric = ::showMasterBiometric,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        mainViewModel.onAppForeground()
        KeepAliveService.start(this)
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) mainViewModel.onAppBackground()
    }

    private fun showMasterBiometric() {
        if (biometricShowing || !mainViewModel.canUseBiometricForMaster()) return
        biometricShowing = true
        biometricPrompt.authenticate(BiometricHelper.promptInfo("Abrir LockApp"))
    }
}
