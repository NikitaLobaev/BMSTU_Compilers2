package lobaevni.compilers.jez

import lobaevni.compilers.jez.Jez.wordEqSat
import lobaevni.compilers.jez.Utils.parseEquation
import lobaevni.compilers.jez.Utils.toStringMap
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class WordEqSat {

    @Test
    fun test1() {
        val sourceEquation = parseEquation("x", "y")
        val expectedSigma = mapOf(
            "x" to "",
            "y" to "",
        )
        val actualSigma = sourceEquation.wordEqSat().sigma.toStringMap()
        assertEquals(expectedSigma, actualSigma)
    }

    @Test
    fun test2() {
        val sourceEquation = parseEquation("A", "A")
        val expectedSigma = emptyMap<String, String>()
        val actualSigma = sourceEquation.wordEqSat().sigma.toStringMap()
        assertEquals(expectedSigma, actualSigma)
    }

    @Test
    fun test3() {
        val sourceEquation = parseEquation("x", "A")
        val expectedSigma = mapOf(
            "x" to "A",
        )
        val actualSigma = sourceEquation.wordEqSat().sigma.toStringMap()
        assertEquals(expectedSigma, actualSigma)
    }

    @Test
    fun test4() {
        val sourceEquation = parseEquation("Ax", "AB")
        val expectedSigma = mapOf(
            "x" to "B",
        )
        val actualSigma = sourceEquation.wordEqSat().sigma.toStringMap()
        assertEquals(expectedSigma, actualSigma)
    }

    @Test
    fun test5() {
        val sourceEquation = parseEquation("Ax", "yB")
        val expectedSigma = mapOf(
            "x" to "B",
            "y" to "A",
        )
        val actualSigma = sourceEquation.wordEqSat().sigma.toStringMap()
        assertEquals(expectedSigma, actualSigma)
    }

    @Test
    fun testNoSolution1() {
        val sourceEquation = parseEquation("A", "B")
        assertThrows<JezNoSolutionException> {
            sourceEquation.wordEqSat()
        }
    }

    @Test
    fun testNoSolution2() {
        val sourceEquation = parseEquation("x", "Ax")
        assertThrows<JezNoSolutionException> {
            sourceEquation.wordEqSat()
        }
    }

    @Test
    fun testNoSolution3() {
        val sourceEquation = parseEquation("AB", "xxx")
        assertThrows<JezNoSolutionException> {
            sourceEquation.wordEqSat()
        }
    }

}
