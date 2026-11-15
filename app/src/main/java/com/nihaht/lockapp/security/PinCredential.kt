package com.nihaht.lockapp.security

import java.util.Base64

/** Tipo de credencial: PIN numérico o patrón 3×3. */
enum class CredentialKind { PIN, PATTERN }

/**
 * Credencial guardada en disco. Nunca contiene el secreto (PIN o patrón):
 * solo su tipo, la sal, el número de iteraciones y el hash derivado con PBKDF2.
 */
class PinCredential(
    val kind: CredentialKind,
    val iterations: Int,
    val salt: ByteArray,
    val hash: ByteArray,
) {
    /** Formato: "v2:TIPO:iteraciones:salBase64:hashBase64" (v1 = PIN, heredado). */
    fun encode(): String {
        val enc = Base64.getEncoder()
        return "$VERSION:${kind.name}:$iterations:${enc.encodeToString(salt)}:${enc.encodeToString(hash)}"
    }

    override fun equals(other: Any?): Boolean =
        other is PinCredential &&
            kind == other.kind &&
            iterations == other.iterations &&
            salt.contentEquals(other.salt) &&
            hash.contentEquals(other.hash)

    override fun hashCode(): Int =
        31 * (31 * (31 * kind.hashCode() + iterations) + salt.contentHashCode()) + hash.contentHashCode()

    companion object {
        private const val VERSION = "v2"

        /** Devuelve null si el texto está corrupto o tiene otro formato. */
        fun decode(text: String?): PinCredential? {
            if (text.isNullOrBlank()) return null
            val parts = text.split(':')
            return try {
                when {
                    // Formato nuevo con tipo: v2:TIPO:iter:sal:hash
                    parts.size == 5 && parts[0] == VERSION -> {
                        val kind = CredentialKind.entries.firstOrNull { it.name == parts[1] } ?: return null
                        build(kind, parts[2], parts[3], parts[4])
                    }
                    // Formato heredado sin tipo (siempre PIN): v1:iter:sal:hash
                    parts.size == 4 && parts[0] == "v1" ->
                        build(CredentialKind.PIN, parts[1], parts[2], parts[3])
                    else -> null
                }
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        private fun build(kind: CredentialKind, iterText: String, saltB64: String, hashB64: String): PinCredential? {
            val dec = Base64.getDecoder()
            val iterations = iterText.toInt()
            val salt = dec.decode(saltB64)
            val hash = dec.decode(hashB64)
            return if (iterations <= 0 || salt.isEmpty() || hash.isEmpty()) null
            else PinCredential(kind, iterations, salt, hash)
        }
    }
}
