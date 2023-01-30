package lobaevni.compilers.jez

sealed class JezElement {

    abstract class Constant : JezElement() {

        abstract val source: JezSourceConstants

        data class Source(
            val value: Any,
        ) : Constant() {

            override val source = listOf(this)

            override fun toString(): String = "CONST($value)"

        }

        data class Generated(
            val value: JezConstants,
        ) : Constant() {

            override val source = value.map { it.source }.flatten()

            val number: Int = (Math.random() * Int.MAX_VALUE).toInt()

            override fun toString(): String = "GENCONST($number)"

        }

    }

    data class Variable(
        val name: Any,
    ) : JezElement() {

        override fun toString(): String = "VAR($name)"

    }

}
