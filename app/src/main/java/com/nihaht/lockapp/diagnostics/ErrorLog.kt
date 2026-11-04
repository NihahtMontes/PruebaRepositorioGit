package com.nihaht.lockapp.diagnostics

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Registro local de errores (nunca sale del teléfono). Sirve para la pantalla
 * de Diagnóstico y para poder copiar un informe cuando algo falla.
 * Ninguna función de aquí lanza excepciones.
 */
object ErrorLog {

    private const val FILE_NAME = "errores.log"
    private const val CRASH_FLAG = "cierre_sin_revisar"
    private const val MAX_BYTES = 64 * 1024
    private const val MAX_TRACE_CHARS = 4000

    private val lock = Any()
    private var dir: File? = null

    fun init(context: Context) {
        dir = context.applicationContext.filesDir
    }

    /** Guarda un error. Con [fatal] se marca como cierre inesperado de la app. */
    fun record(where: String, error: Throwable?, fatal: Boolean = false) {
        val base = dir ?: return
        try {
            val text = buildString {
                append(timestamp())
                append(if (fatal) " CIERRE " else " ERROR ")
                append(where)
                append('\n')
                if (error != null) {
                    append(Log.getStackTraceString(error).ifEmpty { error.toString() }.take(MAX_TRACE_CHARS))
                    append('\n')
                }
            }
            synchronized(lock) {
                val file = File(base, FILE_NAME)
                if (file.length() > MAX_BYTES) {
                    file.writeText(file.readText().takeLast(MAX_BYTES / 2))
                }
                file.appendText(text)
                if (fatal) File(base, CRASH_FLAG).writeText("1")
            }
            Log.e("LockApp", where, error)
        } catch (_: Throwable) {
            // El registro nunca debe provocar otro error.
        }
    }

    fun read(): String = try {
        synchronized(lock) { dir?.let { File(it, FILE_NAME) }?.takeIf { it.exists() }?.readText().orEmpty() }
    } catch (_: Throwable) {
        ""
    }

    /** Número de errores registrados. */
    fun count(): Int = read().lineSequence().count { ENTRY.containsMatchIn(it) }

    /** Primera línea del último error, para mostrarla en pantalla. */
    fun lastEntry(): String? = read().lineSequence().lastOrNull { ENTRY.containsMatchIn(it) }

    fun clear() {
        try {
            synchronized(lock) {
                dir?.let {
                    File(it, FILE_NAME).delete()
                    File(it, CRASH_FLAG).delete()
                }
            }
        } catch (_: Throwable) {
        }
    }

    /** true si la app se cerró por un error y el usuario aún no lo ha visto. */
    fun hasUnseenCrash(): Boolean = try {
        dir?.let { File(it, CRASH_FLAG).exists() } ?: false
    } catch (_: Throwable) {
        false
    }

    fun markCrashSeen() {
        try {
            dir?.let { File(it, CRASH_FLAG).delete() }
        } catch (_: Throwable) {
        }
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

    private val ENTRY = Regex("""^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2} (ERROR|CIERRE) """)
}
