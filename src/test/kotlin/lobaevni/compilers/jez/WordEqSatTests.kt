package lobaevni.compilers.jez

import lobaevni.compilers.jez.Jez.wordEqSat
import lobaevni.compilers.jez.Utils.parseEquation
import lobaevni.compilers.jez.Utils.toStringMap
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class WordEqSatTests {

    @Test
    fun test1() {
        val sourceEquation = parseEquation("x", "y")
        val expectedSigma = mapOf(
            "x" to "",
            "y" to "",
        )
        val result = sourceEquation.wordEqSat()
        assertTrue(result.isSolved)
        val actualSigma = result.sigma.toStringMap()
        assertEquals(expectedSigma, actualSigma)
    }

    @Test
    fun test2() {
        val sourceEquation = parseEquation("A", "A")
        val expectedSigma = emptyMap<String, String>()
        val result = sourceEquation.wordEqSat()
        assertTrue(result.isSolved)
        val actualSigma = result.sigma.toStringMap()
        assertEquals(expectedSigma, actualSigma)
    }

    @Test
    fun test3() {
        val sourceEquation = parseEquation("x", "A")
        val expectedSigma = mapOf(
            "x" to "A",
        )
        val result = sourceEquation.wordEqSat()
        assertTrue(result.isSolved)
        val actualSigma = result.sigma.toStringMap()
        assertEquals(expectedSigma, actualSigma)
    }

    @Test
    fun test4() {
        val sourceEquation = parseEquation("Ax", "AB")
        val expectedSigma = mapOf(
            "x" to "B",
        )
        val result = sourceEquation.wordEqSat()
        assertTrue(result.isSolved)
        val actualSigma = result.sigma.toStringMap()
        assertEquals(expectedSigma, actualSigma)
    }

    @Test
    fun test5() {
        val sourceEquation = parseEquation("ABxy", "yBAx")
        val expectedSigma = mapOf(
            "x" to "",
            "y" to "A",
        )
        val result = sourceEquation.wordEqSat()
        assertTrue(result.isSolved)
        val actualSigma = result.sigma.toStringMap()
        assertEquals(expectedSigma, actualSigma)
    }

    @Test
    fun testNoSolution1() {
        val sourceEquation = parseEquation("A", "B")
        val result = sourceEquation.wordEqSat()
        assertFalse(result.isSolved)
    }

    @Test
    fun testNoSolution2() {
        val sourceEquation = parseEquation("x", "Ax")
        val result = sourceEquation.wordEqSat()
        assertFalse(result.isSolved)
    }

}
