package com.nihaht.lockapp.security

import java.util.Base64

/**
 * Credencial guardada en disco. Nunca contiene el PIN: solo la sal,
 * el número de iteraciones y el hash derivado con PBKDF2.
 */
class PinCredential(
    val iterations: Int,
    val salt: ByteArray,
    val hash: ByteArray,
) {
    /** Formato de texto: "v1:iteraciones:salBase64:hashBase64". */
    fun encode(): String {
        val enc = Base64.getEncoder()
        return "$VERSION:$iterations:${enc.encodeToString(salt)}:${enc.encodeToString(hash)}"
    }

    override fun equals(other: Any?): Boolean =
        other is PinCredential &&
            iterations == other.iterations &&
            salt.contentEquals(other.salt) &&
            hash.contentEquals(other.hash)

    override fun hashCode(): Int =
        31 * (31 * iterations + salt.contentHashCode()) + hash.contentHashCode()

    companion object {
        private const val VERSION = "v1"

        /** Devuelve null si el texto está corrupto o tiene otro formato. */
        fun decode(text: String?): PinCredential? {
            if (text.isNullOrBlank()) return null
            val parts = text.split(':')
            if (parts.size != 4 || parts[0] != VERSION) return null
            return try {
                val dec = Base64.getDecoder()
                val iterations = parts[1].toInt()
                val salt = dec.decode(parts[2])
                val hash = dec.decode(parts[3])
                if (iterations <= 0 || salt.isEmpty() || hash.isEmpty()) null
                else PinCredential(iterations, salt, hash)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}
