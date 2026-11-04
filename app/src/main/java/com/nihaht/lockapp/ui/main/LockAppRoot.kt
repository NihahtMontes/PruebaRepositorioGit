package com.nihaht.lockapp.ui.main

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nihaht.lockapp.appContainer
import com.nihaht.lockapp.data.AppSettings
import com.nihaht.lockapp.diagnostics.ErrorLog
import com.nihaht.lockapp.permissions.PermissionChecker
import com.nihaht.lockapp.ui.applist.AppListScreen
import com.nihaht.lockapp.ui.applist.AppListViewModel
import com.nihaht.lockapp.ui.applist.AppLockEditorScreen
import com.nihaht.lockapp.ui.diagnostics.DiagnosticsScreen
import com.nihaht.lockapp.ui.diagnostics.DiagnosticsViewModel
import com.nihaht.lockapp.ui.gate.MasterSetupScreen
import com.nihaht.lockapp.ui.gate.MasterUnlockScreen
import com.nihaht.lockapp.ui.permissions.PermissionsScreen
import com.nihaht.lockapp.ui.settings.BackgroundScreen
import com.nihaht.lockapp.ui.settings.ChangeMasterScreen
import com.nihaht.lockapp.ui.settings.SettingsScreen

private object Routes {
    const val APPS = "apps"
    const val APP = "app/{pkg}"
    const val SETTINGS = "settings"
    const val CHANGE_MASTER = "change_master"
    const val BACKGROUND = "background"
    const val PERMISSIONS = "permissions"
    const val ONBOARDING = "onboarding"
    const val DIAGNOSTICS = "diagnostics"

    fun app(packageName: String) = "app/${Uri.encode(packageName)}"
}

/**
 * Solo ejecuta [block] si esta pantalla sigue activa. Evita que dos toques
 * rápidos en "Atrás" saquen dos pantallas y dejen la app en blanco.
 */
private inline fun NavBackStackEntry.ifResumed(block: () -> Unit) {
    if (lifecycle.currentState == Lifecycle.State.RESUMED) block()
}

/** Raíz de la interfaz: primero el PIN maestro y después la navegación normal. */
@Composable
fun LockAppRoot(
    mainViewModel: MainViewModel,
    appListViewModel: AppListViewModel,
    onRequestMasterBiometric: () -> Unit,
) {
    val gate by mainViewModel.gate.collectAsStateWithLifecycle()
    val busy by mainViewModel.busy.collectAsStateWithLifecycle()
    val masterEntry by mainViewModel.masterEntry.state.collectAsStateWithLifecycle()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (gate) {
            Gate.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            Gate.SETUP -> MasterSetupScreen(busy = busy, onPinChosen = mainViewModel::createMasterPin)
            Gate.LOCKED -> {
                val biometric = mainViewModel.canUseBiometricForMaster()
                LaunchedEffect(Unit) {
                    if (biometric && masterEntry.lockoutRemainingMs == 0L) onRequestMasterBiometric()
                }
                MasterUnlockScreen(
                    entry = masterEntry,
                    biometricAvailable = biometric,
                    onDigit = mainViewModel.masterEntry::onDigit,
                    onDelete = mainViewModel.masterEntry::onDelete,
                    onSubmit = mainViewModel.masterEntry::onSubmit,
                    onBiometric = onRequestMasterBiometric,
                )
            }
            Gate.UNLOCKED -> {
                val settings by mainViewModel.settings.collectAsStateWithLifecycle()
                settings?.let { MainNavigation(it, mainViewModel, appListViewModel) }
            }
        }
    }
}

@Composable
private fun MainNavigation(
    settings: AppSettings,
    mainViewModel: MainViewModel,
    appListViewModel: AppListViewModel,
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val busy by mainViewModel.busy.collectAsStateWithLifecycle()
    val openDiagnostics by mainViewModel.openDiagnostics.collectAsStateWithLifecycle()
    var accessibilityEnabled by remember { mutableStateOf(PermissionChecker.isAccessibilityServiceEnabled(context)) }
    var serviceResponding by remember { mutableStateOf(true) }
    var crashToReview by remember { mutableStateOf(ErrorLog.hasUnseenCrash()) }
    // Si falta el servicio de accesibilidad se empieza por el asistente de permisos.
    val start = remember { if (accessibilityEnabled) Routes.APPS else Routes.ONBOARDING }

    LifecycleResumeEffect(Unit) {
        accessibilityEnabled = PermissionChecker.isAccessibilityServiceEnabled(context)
        serviceResponding = !accessibilityEnabled || context.appContainer.status.accessibilityConnected
        crashToReview = ErrorLog.hasUnseenCrash()
        appListViewModel.refresh()
        onPauseOrDispose { }
    }

    // Abierto desde un aviso: ir directamente al diagnóstico.
    LaunchedEffect(openDiagnostics) {
        if (openDiagnostics) {
            mainViewModel.consumeOpenDiagnostics()
            navController.navigate(Routes.DIAGNOSTICS) { launchSingleTop = true }
        }
    }

    NavHost(navController = navController, startDestination = start) {
        composable(Routes.ONBOARDING) { entry ->
            PermissionsScreen(
                isOnboarding = true,
                onOpeningSystemSettings = mainViewModel::allowSystemSettingsTemporarily,
                onContinue = {
                    entry.ifResumed {
                        navController.navigate(Routes.APPS) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    }
                },
                onBack = { entry.ifResumed { navController.popBackStack() } },
            )
        }
        composable(Routes.PERMISSIONS) { entry ->
            PermissionsScreen(
                isOnboarding = false,
                onOpeningSystemSettings = mainViewModel::allowSystemSettingsTemporarily,
                onContinue = { entry.ifResumed { navController.popBackStack() } },
                onBack = { entry.ifResumed { navController.popBackStack() } },
            )
        }
        composable(Routes.APPS) { entry ->
            AppListScreen(
                viewModel = appListViewModel,
                paused = settings.paused,
                accessibilityEnabled = accessibilityEnabled,
                serviceResponding = serviceResponding,
                crashToReview = crashToReview,
                onOpenApp = { pkg -> entry.ifResumed { navController.navigate(Routes.app(pkg)) } },
                onOpenSettings = { entry.ifResumed { navController.navigate(Routes.SETTINGS) } },
                onOpenPermissions = { entry.ifResumed { navController.navigate(Routes.PERMISSIONS) } },
                onOpenDiagnostics = { entry.ifResumed { navController.navigate(Routes.DIAGNOSTICS) } },
            )
        }
        composable(
            route = Routes.APP,
            arguments = listOf(navArgument("pkg") { type = NavType.StringType }),
        ) { entry ->
            val packageName = entry.arguments?.getString("pkg").orEmpty()
            AppLockEditorScreen(
                packageName = packageName,
                viewModel = appListViewModel,
                biometricHardware = settings.biometricEnabled && mainViewModel.isBiometricHardwareAvailable(),
                onBack = { entry.ifResumed { navController.popBackStack() } },
            )
        }
        composable(Routes.SETTINGS) { entry ->
            SettingsScreen(
                settings = settings,
                biometricHardware = mainViewModel.isBiometricHardwareAvailable(),
                onBack = { entry.ifResumed { navController.popBackStack() } },
                onChangeMaster = {
                    entry.ifResumed {
                        mainViewModel.startChangeMaster()
                        navController.navigate(Routes.CHANGE_MASTER)
                    }
                },
                onBiometricChange = mainViewModel::setBiometricEnabled,
                onPausedChange = mainViewModel::setPaused,
                onGraceChange = mainViewModel::setGracePeriod,
                onRelockChange = mainViewModel::setRelockOnScreenOff,
                onProtectRecentsChange = mainViewModel::setProtectRecents,
                onOpenBackground = { entry.ifResumed { navController.navigate(Routes.BACKGROUND) } },
                onOpenPermissions = { entry.ifResumed { navController.navigate(Routes.PERMISSIONS) } },
                onOpenDiagnostics = { entry.ifResumed { navController.navigate(Routes.DIAGNOSTICS) } },
                onLockNow = mainViewModel::lockNow,
            )
        }
        composable(Routes.CHANGE_MASTER) { entry ->
            val step by mainViewModel.changeMasterStep.collectAsStateWithLifecycle()
            val verifyEntry by mainViewModel.changeMasterEntry.state.collectAsStateWithLifecycle()
            ChangeMasterScreen(
                step = step,
                verifyEntry = verifyEntry,
                busy = busy,
                onDigit = mainViewModel.changeMasterEntry::onDigit,
                onDelete = mainViewModel.changeMasterEntry::onDelete,
                onSubmit = mainViewModel.changeMasterEntry::onSubmit,
                onNewPin = mainViewModel::saveNewMasterPin,
                onBack = { entry.ifResumed { navController.popBackStack() } },
            )
        }
        composable(Routes.BACKGROUND) { entry ->
            BackgroundScreen(
                current = settings.background,
                busy = busy,
                onSelect = mainViewModel::setBackground,
                onImagePicked = mainViewModel::setImageBackground,
                onBack = { entry.ifResumed { navController.popBackStack() } },
            )
        }
        composable(Routes.DIAGNOSTICS) { entry ->
            val diagnosticsViewModel: DiagnosticsViewModel = viewModel()
            DiagnosticsScreen(
                viewModel = diagnosticsViewModel,
                onBack = { entry.ifResumed { navController.popBackStack() } },
                onOpenAccessibilitySettings = {
                    mainViewModel.allowSystemSettingsTemporarily()
                    context.openSystemScreen(PermissionChecker.accessibilitySettingsIntent())
                },
                onOpenPermissions = { entry.ifResumed { navController.navigate(Routes.PERMISSIONS) } },
                onCopied = { context.appContainer.errors.info("Informe copiado. Pégalo en el chat.") },
            )
        }
    }
}

private fun Context.openSystemScreen(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        appContainer.errors.report("No se pudo abrir esa pantalla de Ajustes", e)
    }
}
