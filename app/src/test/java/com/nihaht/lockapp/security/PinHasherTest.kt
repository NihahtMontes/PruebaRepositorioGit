package com.nihaht.lockapp.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {

    // Pocas iteraciones para que los tests sean rápidos; la lógica es la misma.
    private val iterations = 1_000

    @Test
    fun `valida longitud y solo digitos`() {
        assertTrue(PinHasher.isValidFormat("1234"))
        assertTrue(PinHasher.isValidFormat("123456"))
        assertFalse(PinHasher.isValidFormat("123"))
        assertFalse(PinHasher.isValidFormat("1234567"))
        assertFalse(PinHasher.isValidFormat("12a4"))
        assertFalse(PinHasher.isValidFormat(""))
    }

    @Test
    fun `verifica el pin correcto y rechaza el incorrecto`() {
        val cred = PinHasher.create("4821", iterations)
        assertTrue(PinHasher.verify("4821", cred))
        assertFalse(PinHasher.verify("4820", cred))
        assertFalse(PinHasher.verify("48210", cred))
        assertFalse(PinHasher.verify("", cred))
    }

    @Test
    fun `el mismo pin genera sal y hash distintos`() {
        val a = PinHasher.create("1111", iterations)
        val b = PinHasher.create("1111", iterations)
        assertFalse(a.salt.contentEquals(b.salt))
        assertFalse(a.hash.contentEquals(b.hash))
        assertNotEquals(a, b)
    }

    @Test
    fun `la credencial no contiene el pin en texto plano`() {
        val encoded = PinHasher.create("987654", iterations).encode()
        assertFalse(encoded.contains("987654"))
    }

    @Test
    fun `codificar y decodificar conserva la credencial`() {
        val cred = PinHasher.create("2468", iterations)
        val decoded = PinCredential.decode(cred.encode())
        assertEquals(cred, decoded)
        assertTrue(PinHasher.verify("2468", decoded!!))
    }

    @Test
    fun `decodificar texto corrupto devuelve null`() {
        assertNull(PinCredential.decode(null))
        assertNull(PinCredential.decode(""))
        assertNull(PinCredential.decode("v1:abc"))
        assertNull(PinCredential.decode("v2:10:AAAA:AAAA"))
        assertNull(PinCredential.decode("v1:x:AAAA:AAAA"))
        assertNull(PinCredential.decode("v1:10:@@@:AAAA"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `crear con formato invalido lanza excepcion`() {
        PinHasher.create("12", iterations)
    }
}
