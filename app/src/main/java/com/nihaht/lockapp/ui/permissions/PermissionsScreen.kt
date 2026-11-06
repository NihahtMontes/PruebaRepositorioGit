@file:OptIn(ExperimentalMaterial3Api::class)

package com.nihaht.lockapp.ui.permissions

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.nihaht.lockapp.appContainer
import com.nihaht.lockapp.data.DetectionMode
import com.nihaht.lockapp.permissions.PermissionChecker
import com.nihaht.lockapp.permissions.PermissionStatus

/**
 * Asistente de permisos. El estado se vuelve a comprobar cada vez que la
 * pantalla vuelve a primer plano, así se actualiza al regresar de Ajustes.
 */
@Composable
fun PermissionsScreen(
    isOnboarding: Boolean,
    detectionMode: DetectionMode,
    onOpeningSystemSettings: () -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var status by remember { mutableStateOf(PermissionChecker.status(context)) }

    LifecycleResumeEffect(Unit) {
        status = PermissionChecker.status(context)
        onPauseOrDispose { }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        status = PermissionChecker.status(context)
        if (!granted) context.safeStart(PermissionChecker.notificationSettingsIntent(context))
    }

    fun openSettings(intent: Intent) {
        onOpeningSystemSettings()
        context.safeStart(intent)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isOnboarding) "Configura LockApp" else "Permisos") },
                navigationIcon = {
                    if (!isOnboarding) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "LockApp necesita algunos permisos para detectar cuándo abres una app bloqueada " +
                    "y para que el sistema no lo cierre. No usa internet ni envía datos.",
                style = MaterialTheme.typography.bodyMedium,
            )

            val ready = PermissionChecker.isProtectionReady(context, detectionMode)

            if (detectionMode == DetectionMode.USAGE_ACCESS) {
                PermissionCard(
                    icon = Icons.Filled.History,
                    title = "1. Acceso al uso (obligatorio)",
                    granted = status.usageAccess,
                    description = "Permite a LockApp saber qué app abres, sin accesibilidad. Solo ve el " +
                        "nombre de la app: no puede leer la pantalla ni lo que escribes. Por eso tu app " +
                        "de banca no se bloqueará por este permiso.\n\n" +
                        "En la lista que se abre, busca LockApp y actívalo.",
                    actionLabel = "Abrir Acceso al uso",
                    onAction = { openSettings(PermissionChecker.usageAccessSettingsIntent()) },
                )
                PermissionCard(
                    icon = Icons.Filled.Layers,
                    title = "2. Mostrar sobre otras apps (obligatorio)",
                    granted = status.overlay,
                    description = "Deja que la pantalla de PIN aparezca encima de la app bloqueada. " +
                        "Sin accesibilidad, este permiso es lo que permite mostrarla.",
                    actionLabel = "Permitir mostrar sobre otras apps",
                    onAction = { openSettings(PermissionChecker.overlaySettingsIntent(context)) },
                )
            } else {
                AccessibilityCard(status, ::openSettings, context)
            }

            PermissionCard(
                icon = Icons.Filled.BatteryChargingFull,
                title = "3. Batería sin restricciones",
                granted = status.batteryUnrestricted,
                description = "Evita que Android detenga LockApp para ahorrar batería. Sin esto, " +
                    "el bloqueo puede dejar de funcionar al cabo de un rato.",
                actionLabel = "Quitar optimización de batería",
                onAction = {
                    onOpeningSystemSettings()
                    context.safeStart(PermissionChecker.batteryExemptionIntent(context)) {
                        context.safeStart(PermissionChecker.appDetailsIntent(context))
                    }
                },
            )

            PermissionCard(
                icon = Icons.Filled.Notifications,
                title = "4. Notificaciones (recomendado)",
                granted = status.notifications,
                description = "LockApp muestra una notificación discreta mientras protege tus apps. " +
                    "Así Android lo trata como un servicio activo.",
                actionLabel = "Permitir notificaciones",
                onAction = {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            context.safeStart(PermissionChecker.notificationSettingsIntent(context))
                        }
                    } catch (e: Exception) {
                        context.appContainer.errors.report("No se pudo pedir el permiso de notificaciones", e)
                    }
                },
            )

            if (detectionMode == DetectionMode.ACCESSIBILITY) {
                PermissionCard(
                    icon = Icons.Filled.Layers,
                    title = "Mostrar sobre otras apps",
                    granted = null,
                    description = "No hace falta en este modo: la pantalla de bloqueo la abre el servicio de accesibilidad.",
                )
            }

            SamsungCard(isSamsung = PermissionChecker.isSamsung) {
                openSettings(PermissionChecker.appDetailsIntent(context))
            }

            if (isOnboarding) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                    Text(if (ready) "Ir a mis apps" else "Continuar sin activar (no bloqueará)")
                }
            }
        }
    }
}

@Composable
private fun AccessibilityCard(
    status: PermissionStatus,
    openSettings: (Intent) -> Unit,
    context: Context,
) {
    PermissionCard(
        icon = Icons.Filled.Accessibility,
        title = "1. Servicio de accesibilidad (obligatorio)",
        granted = status.accessibility,
        description = "Permite saber qué app se abre para mostrar el PIN encima. Solo se lee el " +
            "nombre de la app, nunca el contenido de la pantalla ni lo que escribes.\n\n" +
            "En Ajustes busca \"Aplicaciones instaladas\" > LockApp y actívalo.\n\n" +
            "Si aparece \"Ajuste restringido\" (pasa con los APK instalados a mano en Android 13+): " +
            "abre la información de la app, toca ⋮ arriba a la derecha > \"Permitir ajustes " +
            "restringidos\" y vuelve a intentarlo.",
        actionLabel = "Abrir ajustes de accesibilidad",
        onAction = { openSettings(PermissionChecker.accessibilitySettingsIntent()) },
        secondaryLabel = if (status.accessibility) null else "Abrir información de la app",
        onSecondary = { openSettings(PermissionChecker.appDetailsIntent(context)) },
    )
}

@Composable
private fun SamsungCard(isSamsung: Boolean, onOpenAppInfo: () -> Unit) {
    PermissionCard(
        icon = Icons.Filled.PhoneAndroid,
        title = if (isSamsung) "5. Ajustes de Samsung (One UI)" else "5. Si tu teléfono es Samsung",
        granted = null,
        description = "One UI cierra las apps en segundo plano de forma agresiva. Para evitarlo:\n\n" +
            "• Ajustes > Batería > Límites de uso en segundo plano > Apps que nunca se suspenden > " +
            "añade LockApp.\n" +
            "• Ajustes > Aplicaciones > LockApp > Batería > Sin restricciones.\n" +
            "• Abre Recientes, toca el icono de LockApp y elige \"Mantener abierta\" para que no se " +
            "cierre al limpiar las apps recientes.\n\n" +
            "Esta comprobación no se puede hacer automáticamente: revísalo a mano.",
        actionLabel = "Abrir información de la app",
        onAction = onOpenAppInfo,
    )
}

/**
 * Tarjeta de un permiso. [granted] null significa "solo informativo".
 */
@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    granted: Boolean?,
    description: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    secondaryLabel: String? = null,
    onSecondary: () -> Unit = {},
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                )
                when (granted) {
                    true -> Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Activo",
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                    false -> Icon(
                        Icons.Filled.RadioButtonUnchecked,
                        contentDescription = "Pendiente",
                        tint = MaterialTheme.colorScheme.error,
                    )
                    null -> Unit
                }
            }
            Text(
                when (granted) {
                    true -> "Activo"
                    false -> "Pendiente"
                    null -> "Información"
                },
                style = MaterialTheme.typography.labelMedium,
                color = when (granted) {
                    true -> MaterialTheme.colorScheme.tertiary
                    false -> MaterialTheme.colorScheme.error
                    null -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(description, style = MaterialTheme.typography.bodyMedium)
            if (actionLabel != null && granted != true) {
                Button(onClick = onAction) { Text(actionLabel) }
            }
            if (secondaryLabel != null) {
                OutlinedButton(onClick = onSecondary) { Text(secondaryLabel) }
            }
        }
    }
}

/** Abre un ajuste; si el fabricante no lo tiene, prueba la alternativa. */
private fun Context.safeStart(intent: Intent, fallback: (() -> Unit)? = null) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        if (fallback != null) fallback() else appContainer.errors.report("Este teléfono no tiene esa pantalla de Ajustes", e)
    } catch (e: Exception) {
        appContainer.errors.report("No se pudo abrir Ajustes", e)
    }
}
