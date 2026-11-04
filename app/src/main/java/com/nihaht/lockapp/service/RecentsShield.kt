package com.nihaht.lockapp.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.nihaht.lockapp.appContainer
import com.nihaht.lockapp.diagnostics.ErrorLog
import com.nihaht.lockapp.security.PinHasher
import com.nihaht.lockapp.security.VerifyResult
import com.nihaht.lockapp.ui.components.formatRemaining
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Capa opaca que tapa la pantalla de Recientes y pide el PIN maestro, para que
 * nadie vea las miniaturas de las apps bloqueadas. Está hecha con vistas de
 * Android normales porque vive dentro del servicio de accesibilidad.
 */
class RecentsShield(
    private val service: AccessibilityService,
    private val scope: CoroutineScope,
    private val onUnlocked: () -> Unit,
    private val onGoHome: () -> Unit,
) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var root: View? = null
    private var dots: TextView? = null
    private var message: TextView? = null
    private val pin = StringBuilder()
    private var checking = false

    val isShowing: Boolean get() = root != null

    fun show() {
        if (root != null || windowManager == null) return
        try {
            val view = build()
            windowManager.addView(view, layoutParams())
            root = view
        } catch (e: Exception) {
            ErrorLog.record("Mostrar la protección de Recientes", e)
            root = null
        }
    }

    fun hide() {
        val view = root ?: return
        root = null
        pin.clear()
        checking = false
        try {
            windowManager?.removeViewImmediate(view)
        } catch (e: Exception) {
            ErrorLog.record("Quitar la protección de Recientes", e)
        }
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        // Sin foco: los gestos de Atrás e Inicio siguen funcionando con normalidad.
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_SECURE,
        PixelFormat.OPAQUE,
    ).apply {
        gravity = Gravity.CENTER
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun build(): View {
        val context = service
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(BACKGROUND)
            setPadding(dp(24), dp(24), dp(24), dp(24))
            isClickable = true // las pulsaciones no pasan a Recientes
        }
        column.addView(text("🔒", 44f))
        column.addView(text("Recientes protegidos", 24f, top = 12))
        column.addView(text("Introduce tu PIN maestro para ver las apps recientes", 14f, top = 4, alpha = 0.8f))
        dots = text(dotsText(), 22f, top = 24).also { column.addView(it) }
        message = text("", 14f, top = 8).also {
            it.setTextColor(ERROR)
            it.minHeight = dp(40)
            column.addView(it)
        }

        val grid = GridLayout(context).apply { columnCount = 3 }
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "✓").forEach { label ->
            grid.addView(key(label))
        }
        column.addView(grid)

        column.addView(
            text("Ir al inicio", 15f, top = 20).apply {
                setTypeface(typeface, Typeface.BOLD)
                setPadding(dp(16), dp(12), dp(16), dp(12))
                setOnClickListener {
                    hide()
                    onGoHome()
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                }
            },
        )
        return column
    }

    private fun key(label: String): TextView = TextView(service).apply {
        text = label
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0x1AFFFFFF)
        }
        contentDescription = when (label) {
            "⌫" -> "Borrar"
            "✓" -> "Aceptar"
            else -> label
        }
        layoutParams = GridLayout.LayoutParams().apply {
            width = dp(72)
            height = dp(72)
            setMargins(dp(10), dp(6), dp(10), dp(6))
        }
        setOnClickListener { onKey(label) }
    }

    private fun onKey(label: String) {
        if (checking) return
        when (label) {
            "⌫" -> if (pin.isNotEmpty()) pin.deleteCharAt(pin.length - 1)
            "✓" -> submit()
            else -> if (pin.length < PinHasher.MAX_LENGTH) {
                pin.append(label)
                message?.text = ""
            }
        }
        dots?.text = dotsText()
    }

    private fun submit() {
        if (pin.length < PinHasher.MIN_LENGTH) {
            message?.text = "El PIN tiene entre 4 y 6 dígitos"
            return
        }
        val attempt = pin.toString()
        checking = true
        scope.launch {
            try {
                when (val result = service.appContainer.authenticator.verifyMaster(attempt)) {
                    VerifyResult.Success -> {
                        hide()
                        onUnlocked()
                    }
                    is VerifyResult.Wrong -> message?.text = if (result.lockedForMs > 0) {
                        "Demasiados intentos. Espera ${formatRemaining(result.lockedForMs)}"
                    } else {
                        "PIN incorrecto"
                    }
                    is VerifyResult.LockedOut ->
                        message?.text = "Demasiados intentos. Espera ${formatRemaining(result.remainingMs)}"
                }
            } catch (e: Exception) {
                ErrorLog.record("Comprobar el PIN en Recientes", e)
                message?.text = "No se pudo comprobar el PIN. Inténtalo de nuevo"
            } finally {
                checking = false
                pin.clear()
                dots?.text = dotsText()
            }
        }
    }

    private fun dotsText(): String =
        (0 until PinHasher.MAX_LENGTH).joinToString(" ") { if (it < pin.length) "●" else "○" }

    private fun text(value: String, sizeSp: Float, top: Int = 0, alpha: Float = 1f) = TextView(service).apply {
        text = value
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        this.alpha = alpha
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(top) }
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), service.resources.displayMetrics).toInt()

    private companion object {
        const val BACKGROUND = 0xFF101418.toInt()
        const val ERROR = 0xFFFFB4AB.toInt()
    }
}
