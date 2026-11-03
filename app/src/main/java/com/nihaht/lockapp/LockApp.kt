package com.nihaht.lockapp

import android.app.Application
import android.content.Context

class LockApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Acceso al contenedor de dependencias desde cualquier Context. */
val Context.appContainer: AppContainer
    get() = (applicationContext as LockApp).container
