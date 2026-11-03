package com.nihaht.lockapp.ui.applist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nihaht.lockapp.appContainer
import com.nihaht.lockapp.data.InstalledApp
import com.nihaht.lockapp.data.LockedApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppRow(val app: InstalledApp, val locked: Boolean)

class AppListViewModel(application: Application) : AndroidViewModel(application) {

    private val container = application.appContainer

    private val installed = MutableStateFlow<List<InstalledApp>?>(null)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _onlyLocked = MutableStateFlow(false)
    val onlyLocked: StateFlow<Boolean> = _onlyLocked.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** null mientras se lee del disco. */
    val lockedApps: StateFlow<Map<String, LockedApp>?> = container.lockedAppsState

    val rows: StateFlow<List<AppRow>?> =
        combine(installed, container.lockedAppsState, _query, _onlyLocked) { apps, locked, q, only ->
            if (apps == null || locked == null) return@combine null
            val text = q.trim()
            apps.asSequence()
                .map { AppRow(it, it.packageName in locked) }
                .filter { !only || it.locked }
                .filter {
                    text.isEmpty() ||
                        it.app.label.contains(text, ignoreCase = true) ||
                        it.app.packageName.contains(text, ignoreCase = true)
                }
                .toList()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val excluded = container.excludedPackages
            withContext(Dispatchers.IO) { excluded.refresh() }
            installed.value = container.installedApps.launchableApps()
                .filter { excluded.canBeLocked(it.packageName) }
        }
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setOnlyLocked(value: Boolean) {
        _onlyLocked.value = value
    }

    fun label(packageName: String): String =
        installed.value?.firstOrNull { it.packageName == packageName }?.label
            ?: container.installedApps.label(packageName)

    /** Bloquea la app con su propio PIN, o cambia el PIN si ya estaba bloqueada. */
    fun lockApp(packageName: String, pin: String, allowBiometric: Boolean, onDone: () -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val credential = container.authenticator.createCredential(pin)
            container.lockedAppsRepository.lock(packageName, credential, allowBiometric)
            container.authenticator.resetAttempts(packageName)
            container.sessions.lock(packageName)
            _busy.value = false
            onDone()
        }
    }

    fun setAllowBiometric(packageName: String, allow: Boolean) {
        viewModelScope.launch { container.lockedAppsRepository.setAllowBiometric(packageName, allow) }
    }

    fun removeLock(packageName: String) {
        viewModelScope.launch {
            container.lockedAppsRepository.remove(packageName)
            container.authenticator.resetAttempts(packageName)
            container.sessions.lock(packageName)
        }
    }
}
