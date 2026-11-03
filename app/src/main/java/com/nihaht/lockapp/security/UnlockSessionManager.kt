package com.nihaht.lockapp.security

/**
 * Recuerda qué app se desbloqueó y cuándo la dejó el usuario, para aplicar
 * el periodo de gracia. Vive solo en memoria: al morir el proceso todo
 * vuelve a quedar bloqueado, que es lo seguro.
 *
 * Mientras el usuario sigue dentro de la app desbloqueada nunca se vuelve a
 * pedir el PIN. El periodo de gracia empieza a contar cuando sale de ella.
 */
class UnlockSessionManager(private val clock: () -> Long) {

    private var activePackage: String? = null
    private val leftAt = HashMap<String, Long>()

    /**
     * Notifica que [packageName] pasó a primer plano.
     * Devuelve true si hay que mostrar la pantalla de bloqueo.
     */
    @Synchronized
    fun onForeground(packageName: String, isLocked: Boolean, graceMs: Long): Boolean {
        val now = clock()
        val current = activePackage
        if (current == packageName) return false

        // El usuario salió de la app que tenía desbloqueada.
        if (current != null) {
            leftAt[current] = now
            activePackage = null
        }
        if (!isLocked) return false

        val left = leftAt[packageName]
        return if (left != null && now - left < graceMs) {
            // Vuelve dentro del periodo de gracia: se reanuda la sesión.
            leftAt.remove(packageName)
            activePackage = packageName
            false
        } else {
            true
        }
    }

    /** El usuario introdujo el PIN correcto para [packageName]. */
    @Synchronized
    fun markUnlocked(packageName: String) {
        leftAt.remove(packageName)
        activePackage = packageName
    }

    /** Olvida todos los desbloqueos (pantalla apagada, cambio de PIN...). */
    @Synchronized
    fun lockAll() {
        activePackage = null
        leftAt.clear()
    }

    /** Olvida el desbloqueo de una sola app. */
    @Synchronized
    fun lock(packageName: String) {
        if (activePackage == packageName) activePackage = null
        leftAt.remove(packageName)
    }
}
