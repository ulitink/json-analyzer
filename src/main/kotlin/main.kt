import com.google.gson.*
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.io.path.exists

fun main(args: Array<String>) {
    val name = args.getOrElse(0) { "data" }.removeSuffix(".json")
    val json = JsonParser.parseString(Files.readString(Path("data/$name.json")))
    val collector = StatisticsCollector()
    collector.visit(json)
    val total = collector.statisticsForElement(json)
    println(collector.statistics.entries.sortedByDescending { it.value }.joinToString("\n") {
        String.format("%20s: %.1f", it.key, it.value * 100.0 / total)
    })

    val flameGraphFolded = collector.flameGraph.map { it.key + " " + it.value }.joinToString("\n")
    Files.writeString(Path("data/$name.txt"), flameGraphFolded)

    val runtime = Runtime.getRuntime()
    if (!Path("flamegraph.pl").exists()) {
        runtime.exec("wget https://raw.githubusercontent.com/brendangregg/FlameGraph/master/flamegraph.pl").waitFor()
        runtime.exec("chmod +x ./flamegraph.pl").waitFor()
    }

    runtime.exec(arrayOf("/bin/bash", "-c", "./flamegraph.pl data/$name.txt > data/$name.svg")).waitFor()
}

class StatisticsCollector {
    private val currentPath:  MutableList<String> = mutableListOf()
    val statistics: MutableMap<String, Int> = mutableMapOf()
    val flameGraph: MutableMap<String, Int> = mutableMapOf()

    fun visit(e: JsonElement) {
        if (e is JsonObject) {
            for ((property, value) in e.entrySet()) {
                addStatistics(property, value)
                currentPath.add(property)
                visit(value)
                currentPath.removeLast()
            }
        }
        else if (e is JsonArray) {
            for (arrayElement in e) {
                visit(arrayElement)
            }
        }
    }

    private fun addStatistics(property: String, value: JsonElement) {
        if (!currentPath.contains(property)) {
            val current = statistics.getOrDefault(property, 0)
            statistics[property] = current + statisticsForElement(value)

            if (currentPath.isNotEmpty() && (value is JsonPrimitive || value is JsonNull)) {
                val key = currentPath.joinToString(";")
                val prev = flameGraph.getOrDefault(key, 0)
                flameGraph[key] = prev + value.asString.length
            }
        }
    }

    fun statisticsForElement(value: JsonElement): Int = value.toString().length
}
