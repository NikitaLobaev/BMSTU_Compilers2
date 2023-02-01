package lobaevni.compilers.jez

data class JezEquation(
    val u: JezEquationPart,
    val v: JezEquationPart,
) {

    /**
     * @return all used in this [JezEquation] constants.
     */
    fun getUsedConstants(): JezConstants =
        (u + v).toSet().filterIsInstance<JezElement.Constant>()

    /**
     * @return all used in this [JezEquation] source constants.
     */
    fun getUsedSourceConstants(): JezSourceConstants =
        (u + v).toSet().filterIsInstance<JezElement.Constant.Source>()

    /**
     * @return all used in this [JezEquation] generated constants.
     */
    fun getUsedGeneratedConstants(): JezGeneratedConstants =
        (u + v).toSet().filterIsInstance<JezElement.Constant.Generated>()

    /**
     * @return all used in this [JezEquation] variables.
     */
    fun getUsedVariables(): JezVariables =
        (u + v).toSet().filterIsInstance<JezElement.Variable>()

    override fun toString(): String {
        return u.convertToString() + " = " + v.convertToString()
    }

}

typealias JezEquationPart = JezElements

private fun JezEquationPart.convertToString(): String {
    return this.map {
        "$it "
    }.joinToString("") {
        it
    }.trim()
}
