package lobaevni.compilers

import lobaevni.compilers.jez.JezElement
import lobaevni.compilers.jez.JezEquation
import lobaevni.compilers.jez.JezEquationPart
import lobaevni.compilers.jez.Jez.wordEqSat
import java.io.File

private val letterRegex = "^[a-zA-Z]+$".toRegex()

fun main() {
    val letters: List<JezElement.Constant.Source>
    val variables: List<JezElement.Variable>
    val equation: JezEquation
    try {
        letters = readln().trim('{', '}').uppercase().split(",").map { JezElement.Constant.Source(it) }
        assert(letters.find { !(it.value as String).isWord() } == null)

        variables = readln().trim('{', '}').lowercase().split(",").map { JezElement.Variable(it) }
        assert(variables.find { !(it.name as String).isWord() } == null)

        val equationString = readln().split(" = ").toMutableList()
        assert(equationString.size == 2)

        val equationParts: MutableList<JezEquationPart> = mutableListOf(mutableListOf(), mutableListOf())
        for (i in 0..1) {
            assert(equationString[i].isNotEmpty())
            do {
                val element: JezElement? = if (equationString[i][0].isUpperCase()) { // constant
                    letters.find { equationString[i].startsWith(it.value as String) }
                } else { // variable
                    variables.find { equationString[i].startsWith(it.name as String) }
                }
                assert(element != null)
                equationParts[i] += listOf(element!!)

                val length: Int = when (element) {
                    is JezElement.Constant.Source -> (element.value as String).length
                    is JezElement.Variable -> (element.name as String).length
                    else -> 0
                }
                equationString[i] = equationString[i].substring(length)
            } while (equationString[i].isNotEmpty())
        }

        equation = JezEquation(equationParts[0], equationParts[1])
    } catch (e: Exception) {
        println("Usage:")
        println("{A,B,...}")
        println("{x,y,...}")
        println("T1 = T2")
        return
    }

    val result = try {
        equation.wordEqSat()
    } catch (e: Exception) {
        println("Unfortunately, equation wasn't solved.")
        e.printStackTrace()
        return
    }

    try {
        for (mapEntry in result.sigma) {
            println("${mapEntry.key} = ${mapEntry.value}")
        }

        val timeMs = System.currentTimeMillis()
        val graphStr = result.history.dot()

        val workingDirectory = File("output")
        workingDirectory.mkdirs()

        val graphDOTFile = File(workingDirectory, "$timeMs.dot")
        graphDOTFile.createNewFile()
        graphDOTFile.printWriter().use { printWriter ->
            printWriter.println(graphStr)
        }

        val resultPNGFile = File(workingDirectory, "$timeMs.png")
        """dot -Tpng ${graphDOTFile.path} -o ${resultPNGFile.path}""".runCommand()
    } catch (e: Exception) {
        e.printStackTrace()
        return
    }
}

/**
 * Creates operating system processes to run specified command.
 */
private fun String.runCommand() {
    ProcessBuilder(*split(" ").toTypedArray())
        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()
}

private fun String.isWord(): Boolean = matches(letterRegex)
