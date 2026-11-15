package com.nihaht.lockapp.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Crea y verifica credenciales (PIN o patrón) con PBKDF2-HMAC-SHA256 y sal
 * aleatoria. Las operaciones son costosas a propósito: llamarlas fuera del
 * hilo principal.
 */
object PinHasher {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 6

    /** Puntos de la cuadrícula 3×3 (0..8) y mínimo de puntos de un patrón. */
    const val PATTERN_NODES = 9
    const val PATTERN_MIN_NODES = 4

    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"

    private val random = SecureRandom()

    /** El PIN debe tener entre 4 y 6 dígitos. */
    fun isValidFormat(pin: String): Boolean =
        pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it in '0'..'9' }

    /** Un patrón válido: entre 4 y 9 puntos distintos, cada uno en 0..8. */
    fun isValidPattern(nodes: List<Int>): Boolean =
        nodes.size in PATTERN_MIN_NODES..PATTERN_NODES &&
            nodes.all { it in 0 until PATTERN_NODES } &&
            nodes.toSet().size == nodes.size

    /** Secreto canónico de un patrón, p. ej. [0,1,2,5] -> "0-1-2-5". */
    fun patternToSecret(nodes: List<Int>): String = nodes.joinToString("-")

    /** Crea una credencial de PIN. */
    fun create(pin: String, iterations: Int = DEFAULT_ITERATIONS): PinCredential {
        require(isValidFormat(pin)) { "El PIN debe tener entre $MIN_LENGTH y $MAX_LENGTH dígitos" }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        return PinCredential(CredentialKind.PIN, iterations, salt, derive(pin, salt, iterations))
    }

    /** Crea una credencial de patrón. */
    fun createPattern(nodes: List<Int>, iterations: Int = DEFAULT_ITERATIONS): PinCredential {
        require(isValidPattern(nodes)) { "El patrón debe unir al menos $PATTERN_MIN_NODES puntos" }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        return PinCredential(CredentialKind.PATTERN, iterations, salt, derive(patternToSecret(nodes), salt, iterations))
    }

    /** Verifica un secreto (dígitos para PIN, "a-b-c" para patrón) contra la credencial. */
    fun verify(secret: String, credential: PinCredential): Boolean {
        if (secret.isEmpty()) return false
        val candidate = derive(secret, credential.salt, credential.iterations)
        // Comparación en tiempo constante para no filtrar información por tiempos.
        return MessageDigest.isEqual(candidate, credential.hash)
    }

    const val DEFAULT_ITERATIONS = 100_000

    private fun derive(secret: String, salt: ByteArray, iterations: Int): ByteArray {
        val chars = secret.toCharArray()
        val spec = PBEKeySpec(chars, salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
            chars.fill('\u0000')
        }
    }
}
