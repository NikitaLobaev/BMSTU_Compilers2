package lobaevni.compilers.jez

import io.github.rchowell.dotlin.DotRootGraph
import io.github.rchowell.dotlin.digraph
import lobaevni.compilers.jez.JezHeuristics.findSideContradictions
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

data class JezResult(
    val sigma: JezSigma,
    val history: DotRootGraph,
    val isSolved: Boolean,
)

internal data class JezState(
    val sigmaLeft: JezSigma = mutableMapOf(),
    val sigmaRight: JezSigma = mutableMapOf(),
    val replaces: JezReplaces = mutableMapOf(),
    val history: JezHistory = JezHistory(),
)

private const val JEZ_HISTORY_MAX_NODES_COUNT: Long = 300

internal data class JezHistory(
    val graph: DotRootGraph = digraph(JEZ_HISTORY_GRAPH_NAME) {},
    private var lastEquationStr: String? = null,
    private var nodesCount: Long = 0,
) {

    fun addEquation(equation: JezEquation, comment: String = "", updateLast: Boolean = true) {
        if (nodesCount == JEZ_HISTORY_MAX_NODES_COUNT) return
        nodesCount++

        val equationStr = "\"$equation\""
        if (equationStr == lastEquationStr) return

        graph.apply {
            if (updateLast) {
                +equationStr
            } else {
                +equationStr + {
                    color = "red"
                }
            }
            lastEquationStr?.let { lastEquationStr ->
                lastEquationStr - equationStr + {
                    label = comment
                }
            }
            if (updateLast) {
                lastEquationStr = equationStr
            }
        }
    }

}

private const val JEZ_HISTORY_GRAPH_NAME = "recompression"

object Jez {

    /**
     * Checking the satisfiability of a word equation.
     */
    fun JezEquation.wordEqSat(
        maxIterationsCount: Int = (u.size + v.size) * 2,
    ): JezResult {
        val state = JezState()
        for (variable in getUsedVariables()) {
            state.sigmaLeft.getOrPut(variable) { emptyList() }
            state.sigmaRight.getOrPut(variable) { emptyList() }
        }
        state.history.addEquation(this)

        var newEquation = this.trySolveTrivial(state)
        var iteration = 0
        while ((newEquation.u.size > 1 || newEquation.v.size > 1) &&
                !newEquation.findSideContradictions() &&
                !newEquation.checkEmptySolution() &&
                iteration < maxIterationsCount) {

            newEquation = newEquation.blockComp(state).trySolveTrivial(state)
            if (newEquation.findSideContradictions() || newEquation.checkEmptySolution()) break

            val letters = newEquation.getSideLetters()
            newEquation = newEquation.pairComp(state, letters.first, letters.second).trySolveTrivial(state)
            iteration++
        }

        val isSolved = newEquation.checkEmptySolution()

        val sigma = state.sigmaLeft
        for (entry in state.sigmaRight) {
            sigma[entry.key] = sigma[entry.key]!! + entry.value.reversed()
        }

        return JezResult(
            sigma,
            state.history.graph,
            isSolved = isSolved,
        )
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

            state.history.addEquation(newEquation, "all blocks of $constant")

            newEquation = newEquation.trySolveTrivial(state)

            if (newEquation.findSideContradictions() || newEquation.checkEmptySolution()) break
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

                state.history.addEquation(newEquation, "($a, $b) --> $gc")

                newEquation = newEquation.trySolveTrivial(state)

                if (newEquation.findSideContradictions() || newEquation.checkEmptySolution()) return newEquation
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
        fun JezEquationPart.popPart(
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

        /**
         * Tries to assume which letter could we use for popping right now via checking prefixes/postfixes according to
         * [left] respectively.
         */
        fun JezEquation.assumeLetter(
            variable: JezElement.Variable,
            letters: JezConstants,
            left: Boolean,
        ): JezElement.Constant? {
            val uLetter: JezElement?
            val vLetter: JezElement?
            if (left) {
                uLetter = u.firstOrNull()
                vLetter = v.firstOrNull()
            } else {
                uLetter = u.lastOrNull()
                vLetter = v.lastOrNull()
            }
            if (uLetter == variable && vLetter is JezElement.Constant && letters.contains(vLetter)) {
                return vLetter
            } else if (vLetter == variable && uLetter is JezElement.Constant && letters.contains(uLetter)) {
                return uLetter
            }
            return letters.firstOrNull()
        }

        var newEquation = this
        for (variable in getUsedVariables()) {
            val firstLetter = newEquation.assumeLetter(variable, lettersRight, true)
            firstLetter?.let {
                val newPossibleEquation = newEquation.copy(
                    u = newEquation.u.popPart(variable, firstLetter, true),
                    v = newEquation.v.popPart(variable, firstLetter, true),
                )
                val comment = "$variable --> ($firstLetter, $variable)"
                if (!newPossibleEquation.findSideContradictions()) {
                    state.addVariablesReplacement(variable, listOf(firstLetter), true)
                    newEquation = newPossibleEquation

                    state.history.addEquation(newEquation, comment)

                    newEquation = newEquation.trySolveTrivial(state)

                    if (newEquation.findSideContradictions() || newEquation.checkEmptySolution()) return newEquation
                } else {
                    state.history.addEquation(newPossibleEquation, comment, false)
                }
            }

            val lastLetter = newEquation.assumeLetter(variable, lettersLeft, false)
            lastLetter?.let {
                val newPossibleEquation = newEquation.copy(
                    u = newEquation.u.popPart(variable, lastLetter, false),
                    v = newEquation.v.popPart(variable, lastLetter, false),
                )
                val comment = "$variable --> ($variable, $lastLetter)"
                if (!newPossibleEquation.findSideContradictions()) {
                    state.addVariablesReplacement(variable, listOf(lastLetter), false)
                    newEquation = newPossibleEquation

                    state.history.addEquation(newEquation, comment)

                    newEquation = newEquation.trySolveTrivial(state)

                    if (newEquation.findSideContradictions() || newEquation.checkEmptySolution()) return newEquation
                } else {
                    state.history.addEquation(newPossibleEquation, comment, false)
                }
            }
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
            .stream()
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
     * Tries to find trivial solutions in current [JezEquation].
     * @return modified [JezEquation], if trivial solutions were successfully found, otherwise source [JezEquation] (but
     * shortened).
     */
    private fun JezEquation.trySolveTrivial(
        state: JezState,
    ): JezEquation {
        val shortenedEquation = shorten(state)
        if (shortenedEquation != this) {
            return shortenedEquation.trySolveTrivial(state)
        }

        if (checkEmptySolution() || u.size != 1 || v.size != 1) return this

        val uFirst = u.first()
        val vFirst = v.first()
        if (u.size == 1 && uFirst is JezElement.Variable) {
            val value = v.filterIsInstance<JezElement.Constant>().toJezSourceConstants()
            state.addVariablesReplacement(uFirst, value, true)
            return JezEquation(
                u = u.replaceVariable(uFirst, value),
                v = v.replaceVariable(uFirst, value),
            )
        } else if (v.size == 1 && vFirst is JezElement.Variable) {
            val value = u.filterIsInstance<JezElement.Constant>().toJezSourceConstants()
            state.addVariablesReplacement(vFirst, value, true)
            return JezEquation(
                u = u.replaceVariable(vFirst, value),
                v = v.replaceVariable(vFirst, value),
            )
        }

        return this
    }

    /**
     * @return whether an empty solution is suitable for this [JezEquation].
     */
    private fun JezEquation.checkEmptySolution(): Boolean =
        u.filterIsInstance<JezElement.Constant>() == v.filterIsInstance<JezElement.Constant>()

    /**
     * Adds new replacement of [repPart] to the [JezReplaces]. Note that it doesn't replace [repPart] in the source
     * equation.
     * @return an existing generated constant for specified [repPart] or newly generated constant for that [repPart].
     */
    private fun JezReplaces.addConstantsReplacement(
        repPart: JezConstants,
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
        repPart: JezConstants,
        left: Boolean,
    ) {
        val sigma = if (left) sigmaLeft else sigmaRight
        sigma[variable] = sigma[variable]!! + repPart.toJezSourceConstants()
    }

    /**
     * @return [JezEquationPart] with specified [repPart] replacement of [variable].
     */
    private fun JezEquationPart.replaceVariable(
        variable: JezElement.Variable,
        repPart: JezConstants,
    ): JezEquationPart {
        return map { element ->
            if (element == variable) {
                repPart
            } else {
                listOf(element)
            }
        }.flatten()
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
