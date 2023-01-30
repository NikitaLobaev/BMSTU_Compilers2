package lobaevni.compilers.jez

data class JezEquation(
    val u: JezEquationPart,
    val v: JezEquationPart,
) {

    /**
     * TODO
     */
    fun getUsedConstants(): JezConstants =
        (u + v).toSet().filterIsInstance<JezElement.Constant>()

    /**
     * TODO
     */
    fun getUsedSourceConstants(): JezSourceConstants =
        (u + v).toSet().filterIsInstance<JezElement.Constant.Source>()

    /**
     * TODO
     */
    fun getUsedGeneratedConstants(): JezGeneratedConstants =
        (u + v).toSet().filterIsInstance<JezElement.Constant.Generated>()

    /**
     * TODO
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
