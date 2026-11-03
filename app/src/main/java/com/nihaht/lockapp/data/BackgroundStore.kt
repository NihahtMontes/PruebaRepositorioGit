package com.nihaht.lockapp.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Copia la imagen elegida con el Photo Picker al almacenamiento interno,
 * reducida de tamaño. Así no hace falta ningún permiso de almacenamiento
 * ni conservar acceso a la URI original.
 */
class BackgroundStore(context: Context) {

    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, FILE_NAME)

    suspend fun saveFromUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val bitmap = decode(uri) ?: return@withContext false
            val tmp = File(appContext.filesDir, "$FILE_NAME.tmp")
            FileOutputStream(tmp).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out) }
            bitmap.recycle()
            if (file.exists()) file.delete()
            tmp.renameTo(file)
        } catch (e: IOException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }

    suspend fun load(): ImageBitmap? = withContext(Dispatchers.IO) {
        if (!file.exists()) null
        else BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
    }

    private fun decode(uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder respeta la orientación EXIF de las fotos.
            val source = ImageDecoder.createSource(appContext.contentResolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val width = info.size.width
                val height = info.size.height
                val scale = min(1f, MAX_SIDE.toFloat() / max(width, height))
                decoder.setTargetSize(
                    (width * scale).roundToInt().coerceAtLeast(1),
                    (height * scale).roundToInt().coerceAtLeast(1),
                )
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val resolver = appContext.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private companion object {
        const val FILE_NAME = "lock_background.jpg"
        const val MAX_SIDE = 1600
        const val JPEG_QUALITY = 88
    }
}
