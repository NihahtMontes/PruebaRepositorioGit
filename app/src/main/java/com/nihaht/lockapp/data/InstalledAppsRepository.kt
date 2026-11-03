package com.nihaht.lockapp.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(val packageName: String, val label: String)

/** Lista las apps con icono en el launcher. Usa <queries> en lugar de QUERY_ALL_PACKAGES. */
class InstalledAppsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val pm: PackageManager = appContext.packageManager
    private val iconCache = LruCache<String, ImageBitmap>(ICON_CACHE_SIZE)

    suspend fun launchableApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryActivitiesCompat(intent)
            .map { info ->
                InstalledApp(
                    packageName = info.activityInfo.packageName,
                    label = info.loadLabel(pm).toString(),
                )
            }
            .filter { it.packageName != appContext.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    /** Nombre visible de la app; si ya no está instalada se devuelve el paquete. */
    fun label(packageName: String): String = try {
        pm.applicationInfoCompat(packageName).loadLabel(pm).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        packageName
    }

    suspend fun icon(packageName: String, sizePx: Int): ImageBitmap? {
        val cacheKey = "$packageName@$sizePx"
        iconCache.get(cacheKey)?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                pm.getApplicationIcon(packageName)
                    .toBitmap(sizePx, sizePx)
                    .asImageBitmap()
                    .also { iconCache.put(cacheKey, it) }
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }
    }

    private companion object {
        const val ICON_CACHE_SIZE = 300
    }
}
