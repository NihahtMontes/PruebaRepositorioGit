package com.nihaht.lockapp

import android.app.Application
import android.content.Context
import com.nihaht.lockapp.diagnostics.ErrorLog
import com.nihaht.lockapp.service.WatchdogWorker

class LockApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        ErrorLog.init(this)
        // Si algo cierra la app, se guarda el motivo para verlo en Diagnóstico.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            ErrorLog.record("Cierre inesperado (${thread.name})", error, fatal = true)
            previous?.uncaughtException(thread, error)
        }
        container = AppContainer(this)
        WatchdogWorker.schedule(this)
    }
}

/** Acceso al contenedor de dependencias desde cualquier Context. */
val Context.appContainer: AppContainer
    get() = (applicationContext as LockApp).container
