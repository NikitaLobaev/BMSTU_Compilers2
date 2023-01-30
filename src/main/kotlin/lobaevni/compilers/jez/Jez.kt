package lobaevni.compilers.jez

import io.github.rchowell.dotlin.DotRootGraph
import io.github.rchowell.dotlin.digraph
import lobaevni.compilers.jez.JezHeuristics.getSideLetters
import lobaevni.compilers.jez.JezHeuristics.shorten

typealias JezSigma = MutableMap<JezElement.Variable, List<JezElement.Constant.Source>>
internal typealias JezReplaces = MutableMap<List<JezElement.Constant.Source>, JezElement.Constant.Generated>

internal typealias JezElements = List<JezElement>
internal typealias JezConstants = List<JezElement.Constant>
internal typealias JezSourceConstants = List<JezElement.Constant.Source>
internal typealias JezGeneratedConstants = List<JezElement.Constant.Generated>
internal typealias JezVariables = List<JezElement.Variable>

open class JezException(message: String) : java.lang.RuntimeException(message)
class JezNoSolutionException : JezException("Couldn't solve the equation")

data class JezResult(
    val sigma: JezSigma,
    val history: DotRootGraph,
)

internal data class JezState(
    val sigmaLeft: JezSigma = mutableMapOf(),
    val sigmaRight: JezSigma = mutableMapOf(),
    val replaces: JezReplaces = mutableMapOf(),
    val history: DotRootGraph = digraph("recompression") {},
)

object Jez {

    /**
     * Checking the satisfiability of a word equation.
     * @throws JezNoSolutionException if no solution was found.
     */
    fun JezEquation.wordEqSat(
        maxIterationsCount: Int = (u.size + v.size) * 2, //TODO: maybe decrease this default value
    ): JezResult {
        val state = JezState()
        for (variable in getUsedVariables()) {
            state.sigmaLeft.getOrPut(variable) { emptyList() }
            state.sigmaRight.getOrPut(variable) { emptyList() }
        }
        state.history.apply {
            +""""$this""""
        }

        var newEquation = this
        var iteration = 0
        while (!newEquation.checkEmptySolution() &&
                newEquation.u.size > 1 && newEquation.v.size > 1 &&
                iteration < maxIterationsCount) {
            newEquation = newEquation.shorten(state)

            newEquation = newEquation.blockComp(state)

            val letters = newEquation.getSideLetters()
            for (i in 1..2) {
                newEquation = newEquation.pairComp(state, letters.first, letters.second)
            }
            iteration++
        }

        if (!newEquation.findTrivialSolutions(state)) {
            throw JezNoSolutionException()
        }

        val sigma = state.sigmaLeft
        for (entry in state.sigmaRight) {
            sigma[entry.key] = sigma[entry.key]!! + entry.value.reversed()
        }

        return JezResult(sigma, state.history)
    }

    /**
     * Compressing blocks of letters.
     */
    private fun JezEquation.blockComp(
        state: JezState,
    ): JezEquation {
        var newEquation = this
        for (constant in getUsedConstants()) {
            newEquation = newEquation.copy(
                u = newEquation.u.blockCompNCr(state, constant),
                v = newEquation.v.blockCompNCr(state, constant),
            )
        }
        return newEquation
    }

    /**
     * Turning crossing pairs from cartesian product [lettersLeft]*[lettersRight] into non-crossing ones and compressing
     * them.
     * @param lettersLeft constants, that might be on the left side of the crossing pair being replaced.
     * @param lettersRight similarly, constants, that might be on the right side.
     */
    private fun JezEquation.pairComp(
        state: JezState,
        lettersLeft: JezConstants,
        lettersRight: JezConstants,
    ): JezEquation {
        var newEquation = pop(state, lettersLeft, lettersRight)

        for (a in lettersLeft) {
            for (b in lettersRight) {
                if (a == b) continue

                val sourcePart = a.source + b.source
                val currentPart = listOf(a, b)
                val gc = state.replaces.getOrPut(sourcePart) { JezElement.Constant.Generated(currentPart) }
                newEquation = newEquation.copy(
                    u = newEquation.u.pairCompNCr(a, b, gc),
                    v = newEquation.v.pairCompNCr(a, b, gc),
                )
            }
        }
        return newEquation
    }

    /**
     * Pairs compression for a crossing pairs.
     * @param lettersLeft constants, that might be on the left side of the crossing pair being replaced.
     * @param lettersRight similarly, constants, that might be on the right side.
     */
    private fun JezEquation.pop(
        state: JezState,
        lettersLeft: JezConstants,
        lettersRight: JezConstants,
    ): JezEquation {
        /**
         * Pops the specified [constant] from the [variable].
         * @param left if true, then pop [constant] from the left side of the [variable], otherwise from the right side.
         */
        fun JezEquationPart.pop(
            variable: JezElement.Variable,
            constant: JezElement.Constant,
            left: Boolean,
        ): JezEquationPart {
            return map { element ->
                if (element == variable) {
                    if (left) {
                        listOf(constant, variable)
                    } else {
                        listOf(variable, constant)
                    }
                } else {
                    listOf(element)
                }
            }.flatten()
        }

        var newEquation = this
        for (variable in getUsedVariables()) {
            break //TODO: эвристика - заглянуть в будущие префиксы, нет ли противоречий
            val firstLetter = lettersRight.firstOrNull()
            firstLetter?.let {
                state.addVariablesReplacement(variable, listOf(firstLetter), true)
                newEquation = newEquation.copy(
                    u = newEquation.u.pop(variable, firstLetter, true),
                    v = newEquation.v.pop(variable, firstLetter, true),
                )
            }

            val lastLetter = lettersLeft.firstOrNull()
            lastLetter?.let {
                state.addVariablesReplacement(variable, listOf(lastLetter), false)
                newEquation = newEquation.copy(
                    u = newEquation.u.pop(variable, lastLetter, false),
                    v = newEquation.v.pop(variable, lastLetter, false),
                )
            }
            break
        }
        return newEquation
    }

    /**
     * Pair compression for a non-crossing pair of [a] and [b] to generated constant [gc].
     */
    private fun JezEquationPart.pairCompNCr(
        a: JezElement.Constant,
        b: JezElement.Constant,
        gc: JezElement.Constant.Generated,
    ): JezEquationPart {
        if (a == b) {
            throw JezException("Couldn't replace block instead of a pair.")
        }

        var result: JezEquationPart = zipWithNext().map { (element1, element2) ->
            if (element1 == a && element2 == b) {
                gc
            } else {
                element1
            }
        }.toMutableList()
        if (result.lastOrNull() != gc) {
            result = result + last()
        }

        return result
    }

    /**
     * Block compression for a letter [a] with no crossing block.
     */
    private fun JezEquationPart.blockCompNCr(
        state: JezState,
        a: JezElement.Constant,
    ): JezEquationPart {
        data class Acc(
            val total: List<JezElement>,
            val element: JezElement,
            val count: Int,
        )

        val newEquationPart = this
            .map { element ->
                Acc(listOf(), element, 1)
            }
            .stream() //TODO: а почему обязательно преобразовывать в stream?
            .reduce(null) { lastAcc, currentAcc ->
                if (lastAcc == null) {
                    currentAcc
                } else {
                    if (lastAcc.element == currentAcc.element) {
                        Acc(lastAcc.total, currentAcc.element, lastAcc.count + 1)
                    } else {
                        if (lastAcc.element == a && lastAcc.count > 1) {
                            val repeatingPart = List(lastAcc.count) { a }.toJezSourceConstants()
                            val newVariable = state.replaces.addConstantsReplacement(repeatingPart)
                            Acc((lastAcc.total + newVariable).toMutableList(), currentAcc.element, 1)
                        } else {
                            val repeatingPart = List(lastAcc.count) { lastAcc.element }
                            Acc((lastAcc.total + repeatingPart).toMutableList(), currentAcc.element, 1)
                        }
                    }
                }
            }.let { acc ->
                Acc(acc.total + acc.element, acc.element, 0)
            }.total
        return newEquationPart
    }

    /**
     * @return whether any of some trivial solutions is suitable for this [JezEquation].
     */
    private fun JezEquation.findTrivialSolutions(
        state: JezState,
    ): Boolean {
        if (checkEmptySolution()) return true

        if (u == v) return true

        if (u.size == 1 && v.size == 1) {
            val uFirst = u.first()
            val vFirst = v.first()
            if (uFirst is JezElement.Constant && vFirst is JezElement.Variable) {
                return JezEquation(v, u).findTrivialSolutions(state)
            }

            if (uFirst !is JezElement.Variable) {
                return false
            }

            if (vFirst is JezElement.Constant) {
                state.addVariablesReplacement(uFirst, vFirst.source, true)
            }
            return true
        }

        return false
    }

    /**
     * @return whether an empty solution is suitable for this [JezEquation]
     */
    private fun JezEquation.checkEmptySolution(): Boolean =
        u.filterIsInstance<JezElement.Constant>() == v.filterIsInstance<JezElement.Constant>()

    /**
     * Adds new replacement of [repPart] to the [JezReplaces]. Note that it doesn't replace [repPart] in the source
     * equation.
     * @return an existing generated constant for specified [repPart] or newly generated constant for that [repPart].
     */
    private fun JezReplaces.addConstantsReplacement(
        repPart: List<JezElement.Constant>,
    ): JezElement.Constant.Generated {
        val repSourcePart = repPart.toJezSourceConstants()
        return getOrPut(repSourcePart) { JezElement.Constant.Generated(repPart) }
    }

    /**
     * Adds new replacement of [repPart] to the [variable] in sigma of this [JezState]. Note that it doesn't replace
     * [repPart] in the source equation.
     * @param left if true, then add replacement from the left side of the [variable], otherwise from the right side.
     */
    private fun JezState.addVariablesReplacement(
        variable: JezElement.Variable,
        repPart: List<JezElement.Constant>,
        left: Boolean,
    ) {
        val sigma = if (left) sigmaLeft else sigmaRight
        sigma[variable] = sigma[variable]!! + repPart.toJezSourceConstants()
    }

    /**
     * Reveals source constant values and returns list of these [JezElement.Constant.Source].
     */
    private fun JezConstants.toJezSourceConstants(): JezSourceConstants {
        return map { constant ->
            constant.source
        }.flatten()
    }

}