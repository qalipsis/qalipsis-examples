package io.qalipsis.example.simple

import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.rampup.more
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.constantPace
import io.qalipsis.api.steps.execute
import io.qalipsis.api.steps.map
import io.qalipsis.api.steps.returns
import io.qalipsis.api.steps.shelve
import io.qalipsis.api.steps.unshelve
import java.time.Duration

/**
 *
 * Example to create a very simple scenario that just logs several times "I'M THE MINION X and finished after .... ms".
 *
 * You can find the generated logs and events in the files in your working directory.
 *
 * @author Eric Jessé
 */
class HelloWorldScenario {

    val minions = 50
    var start = System.currentTimeMillis()

    @Scenario
    fun myScenario() {
        scenario("hello-world") {
            minionsCount = minions
            rampUp {
                more(200, 10, 1.1, 10000)
            }
        }
            .start()
            .returns<String> { context ->
                "Hello World! I'm the minion ${context.minionId}"
            }.configure {
                name = "entry"
            }
            .shelve { mapOf("started at" to System.currentTimeMillis()) }
            .map { str -> str.toUpperCase() }.configure {
                name = "map-1"
            }
            .constantPace(Duration.ofMillis(100))
            .unshelve<String, Long>("started at")
            .execute<Pair<String, Long?>, Unit> { ctx ->
                val input = ctx.receive()
                logger.debug { "${input.first} and finished after ${input.second!! - start} ms" }
            }
            .configure {
                name = "log"
                iterations = 2
            }
    }

    companion object {

        @JvmStatic
        private val logger = logger()
    }
}
