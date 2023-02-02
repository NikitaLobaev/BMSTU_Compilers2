package lobaevni.compilers.jez

import lobaevni.compilers.jez.JezHeuristics.shorten

object JezHeuristics {

    /**
     * Heuristic of shortening of the [JezEquation]. Cuts similar starts and ends of the left and right side of the
     * [JezEquation].
     */
    internal fun JezEquation.shorten(state: JezState): JezEquation {
        var newEquation = this
        val leftIndex = newEquation.u.zip(newEquation.v).indexOfFirst { (uElement, vElement) ->
            uElement != vElement
        }.takeIf { it != -1 } ?: minOf(newEquation.u.size, newEquation.v.size)
        newEquation = JezEquation(
            u = newEquation.u.drop(leftIndex),
            v = newEquation.v.drop(leftIndex),
        )

        val rightIndex = newEquation.u.reversed().zip(newEquation.v.reversed()).indexOfFirst { (uElement, vElement) ->
            uElement != vElement
        }.takeIf { it != -1 } ?: minOf(newEquation.u.size, newEquation.v.size)
        newEquation = JezEquation(
            u = newEquation.u.dropLast(rightIndex),
            v = newEquation.v.dropLast(rightIndex),
        )

        state.history.addEquation(newEquation, "shorten")

        return newEquation
    }

    /**
     * Heuristic of finding contradictions in the [JezEquation] at left or at right sides of it.
     * @return true, if contradiction was found, false otherwise.
     */
    internal fun JezEquation.findSideContradictions(): Boolean {
        val shortenedEquation = shorten(JezState())
        return (shortenedEquation.u.firstOrNull() is JezElement.Constant &&
                shortenedEquation.v.firstOrNull() is JezElement.Constant) ||
                (shortenedEquation.u.lastOrNull() is JezElement.Constant &&
                shortenedEquation.v.lastOrNull() is JezElement.Constant) ||
                (shortenedEquation.u.isEmpty() && shortenedEquation.v.find { it is JezElement.Constant } != null) ||
                (shortenedEquation.v.isEmpty() && shortenedEquation.u.find { it is JezElement.Constant } != null)
    }

    /**
     * Heuristic to determine what pairs of constants we might count as non-crossing.
     * @return pair of left and right constants lists respectively.
     */
    internal fun JezEquation.getSideLetters(): Pair<JezConstants, JezConstants> {
        fun JezEquationPart.findExcludedLetters(): Pair<Set<JezElement.Constant>, Set<JezElement.Constant>> {
            val lettersLeftExcluded = mutableSetOf<JezElement.Constant>()
            val lettersRightExcluded = mutableSetOf<JezElement.Constant>()
            forEachIndexed { index, element ->
                if (element !is JezElement.Constant) {
                    return@forEachIndexed
                }

                if (index > 0 && elementAt(index - 1) is JezElement.Variable) {
                    lettersRightExcluded += element
                }
                if (index + 1 < size && elementAt(index + 1) is JezElement.Variable) {
                    lettersLeftExcluded += element
                }
            }
            return Pair(lettersLeftExcluded, lettersRightExcluded)
        }

        val letters = getUsedConstants()
        val uExcludedLetters = u.findExcludedLetters()
        val vExcludedLetters = v.findExcludedLetters()
        val leftLetters = letters.toMutableSet().apply {
            removeAll(uExcludedLetters.first)
            removeAll(vExcludedLetters.first)
        }
        val rightLetters = letters.toMutableSet().apply {
            removeAll(uExcludedLetters.second)
            removeAll(vExcludedLetters.second)
        }

        return Pair(leftLetters.toMutableList(), rightLetters.toMutableList())
    }

}
