package lobaevni.compilers

import lobaevni.compilers.jez.*
import lobaevni.compilers.jez.Jez.wordEqSat
import java.io.File

private val letterRegex = "^[a-zA-Z]+$".toRegex()

fun main() {
    val letters: JezSourceConstants
    val variables: JezVariables
    val equation: JezEquation
    try {
        letters = parseLetters()
        variables = parseVariables()
        equation = parseEquation(letters, variables)
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
        println("Unfortunately, exception was thrown while solving the equation.")
        return
    }

    try {
        if (result.isSolved) {
            printResult(result)
        } else {
            println("Unfortunately, solution wasn't found.")
        }
        writeDOT(result)
    } catch (e: Exception) {
        e.printStackTrace()
        return
    }
}

private fun parseLetters(): JezSourceConstants {
    val letters = readln().trim('{', '}').uppercase().split(",").map { JezElement.Constant.Source(it) }
    assert(letters.find { !(it.value as String).isWord() } == null)
    return letters
}

private fun parseVariables(): JezVariables {
    val variables = readln().trim('{', '}').lowercase().split(",").map { JezElement.Variable(it) }
    assert(variables.find { !(it.name as String).isWord() } == null)
    return variables
}

private fun parseEquation(letters: JezSourceConstants, variables: JezVariables): JezEquation {
    val equationString = readln().split(" = ").toMutableList()
    assert(equationString.size == 2)

    val equationParts: MutableList<JezEquationPart> = mutableListOf(mutableListOf(), mutableListOf())
    for (i in 0..1) {
        assert(equationString[i].isNotEmpty())
        do {
            val element: JezElement? = if (equationString[i][0].isUpperCase()) {
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

    return JezEquation(equationParts[0], equationParts[1])
}

private fun printResult(result: JezResult) {
    for (mapEntry in result.sigma) {
        println("${mapEntry.key} = ${mapEntry.value}")
    }
}

private fun writeDOT(result: JezResult) {
    println()
    print("Writing DOT-representation...")
    val timeMs = System.currentTimeMillis()
    val graphStr = result.history.dot()

    val workingDirectory = File("output")
    workingDirectory.mkdirs()

    val graphDOTFile = File(workingDirectory, "$timeMs.dot")
    graphDOTFile.createNewFile()
    graphDOTFile.printWriter().use { printWriter ->
        printWriter.println(graphStr)
        printWriter.flush()
        printWriter.close()
    }

    val resultPNGFile = File(workingDirectory, "$timeMs.png")
    """dot -Tpng ${graphDOTFile.path} -o ${resultPNGFile.path}""".runCommand()

    println(" SUCCESS")
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
