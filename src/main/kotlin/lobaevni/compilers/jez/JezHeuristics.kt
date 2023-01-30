package lobaevni.compilers.jez

object JezHeuristics {

    /**
     * Heuristic of shortening of the [JezEquation]. Cuts similar starts and ends of the left and right side of the
     * [JezEquation].
     */
    internal fun JezEquation.shorten(state: JezState): JezEquation {
        val leftIndex = u.zip(v).indexOfFirst { (uElement, vElement) ->
            uElement != vElement
        }
        val rightIndex = u.reversed().zip(v.reversed()).indexOfFirst { (uElement, vElement) ->
            uElement != vElement
        }
        val newEquation = JezEquation(
            u = u.subList(leftIndex, u.size - rightIndex),
            v = v.subList(leftIndex, v.size - rightIndex),
        )
        state.history.apply {
            +""""$newEquation""""
        }
        return newEquation
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
