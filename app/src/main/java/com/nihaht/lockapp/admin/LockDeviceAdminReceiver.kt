package com.nihaht.lockapp.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * Administrador de dispositivos. No pide ninguna capacidad real: solo estar
 * activo impide que Android desinstale LockApp de un toque (hay que desactivar
 * el administrador primero). No controla nada del teléfono.
 */
class LockDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Si desactivas el administrador, LockApp se podrá desinstalar. " +
            "Hazlo solo si de verdad quieres quitar la protección."
}
