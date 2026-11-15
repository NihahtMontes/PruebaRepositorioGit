package com.nihaht.lockapp.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternHasherTest {

    private val iterations = 1_000

    @Test
    fun `valida numero de puntos y rango`() {
        assertTrue(PinHasher.isValidPattern(listOf(0, 1, 2, 5)))
        assertTrue(PinHasher.isValidPattern(listOf(0, 1, 2, 3, 4, 5, 6, 7, 8)))
        assertFalse(PinHasher.isValidPattern(listOf(0, 1, 2)))        // pocos
        assertFalse(PinHasher.isValidPattern(listOf(0, 1, 1, 2)))     // repetido
        assertFalse(PinHasher.isValidPattern(listOf(0, 1, 2, 9)))     // fuera de rango
        assertFalse(PinHasher.isValidPattern(emptyList()))
    }

    @Test
    fun `secreto canonico`() {
        assertEquals("0-1-2-5", PinHasher.patternToSecret(listOf(0, 1, 2, 5)))
    }

    @Test
    fun `verifica el patron correcto y rechaza el incorrecto`() {
        val cred = PinHasher.createPattern(listOf(0, 3, 6, 7), iterations)
        assertEquals(CredentialKind.PATTERN, cred.kind)
        assertTrue(PinHasher.verify(PinHasher.patternToSecret(listOf(0, 3, 6, 7)), cred))
        assertFalse(PinHasher.verify(PinHasher.patternToSecret(listOf(0, 3, 6, 8)), cred))
        // El orden importa.
        assertFalse(PinHasher.verify(PinHasher.patternToSecret(listOf(7, 6, 3, 0)), cred))
    }

    @Test
    fun `el mismo patron genera sal y hash distintos`() {
        val a = PinHasher.createPattern(listOf(0, 1, 2, 5), iterations)
        val b = PinHasher.createPattern(listOf(0, 1, 2, 5), iterations)
        assertFalse(a.salt.contentEquals(b.salt))
        assertNotEquals(a, b)
    }

    @Test
    fun `la credencial no contiene el patron en texto plano`() {
        val encoded = PinHasher.createPattern(listOf(0, 1, 2, 5, 8), iterations).encode()
        assertFalse(encoded.contains("0-1-2-5-8"))
        assertTrue(encoded.startsWith("v2:PATTERN:"))
    }

    @Test
    fun `codificar y decodificar conserva tipo patron`() {
        val cred = PinHasher.createPattern(listOf(1, 4, 7, 8), iterations)
        val decoded = PinCredential.decode(cred.encode())
        assertEquals(cred, decoded)
        assertEquals(CredentialKind.PATTERN, decoded!!.kind)
    }

    @Test
    fun `una credencial v1 heredada se lee como PIN`() {
        val pin = PinHasher.create("4821", iterations)
        // Formato antiguo v1 (sin tipo).
        val v1 = pin.encode().replace("v2:PIN:", "v1:")
        val decoded = PinCredential.decode(v1)
        assertEquals(CredentialKind.PIN, decoded!!.kind)
        assertTrue(PinHasher.verify("4821", decoded))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `crear patron invalido lanza excepcion`() {
        PinHasher.createPattern(listOf(0, 1), iterations)
    }
}
