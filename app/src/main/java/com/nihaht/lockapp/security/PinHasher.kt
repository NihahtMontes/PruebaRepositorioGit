package com.nihaht.lockapp.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Crea y verifica PIN con PBKDF2-HMAC-SHA256 y sal aleatoria.
 * Las operaciones son costosas a propósito: llamarlas fuera del hilo principal.
 */
object PinHasher {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 6
    const val DEFAULT_ITERATIONS = 100_000

    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"

    private val random = SecureRandom()

    /** El PIN debe tener entre 4 y 6 dígitos. */
    fun isValidFormat(pin: String): Boolean =
        pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it in '0'..'9' }

    fun create(pin: String, iterations: Int = DEFAULT_ITERATIONS): PinCredential {
        require(isValidFormat(pin)) { "El PIN debe tener entre $MIN_LENGTH y $MAX_LENGTH dígitos" }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        return PinCredential(iterations, salt, derive(pin, salt, iterations))
    }

    fun verify(pin: String, credential: PinCredential): Boolean {
        if (!isValidFormat(pin)) return false
        val candidate = derive(pin, credential.salt, credential.iterations)
        // Comparación en tiempo constante para no filtrar información por tiempos.
        return MessageDigest.isEqual(candidate, credential.hash)
    }

    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val chars = pin.toCharArray()
        val spec = PBEKeySpec(chars, salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
            chars.fill('\u0000')
        }
    }
}
