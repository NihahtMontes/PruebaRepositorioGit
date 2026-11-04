package com.nihaht.lockapp.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Vuelve a iniciar la protección tras reiniciar el teléfono o actualizar la app. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                KeepAliveService.start(context)
                WatchdogWorker.schedule(context)
            }
        }
    }
}
