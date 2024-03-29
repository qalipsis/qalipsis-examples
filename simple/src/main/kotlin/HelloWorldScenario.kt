/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package io.qalipsis.example.simple

import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.executionprofile.more
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.constantPace
import io.qalipsis.api.steps.execute
import io.qalipsis.api.steps.map
import io.qalipsis.api.steps.returns
import io.qalipsis.api.steps.shelve
import io.qalipsis.api.steps.unshelve
import mu.KotlinLogging
import java.time.Duration
import java.util.Locale

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

    @Scenario("hello-world")
    fun myScenario() {
        scenario {
            minionsCount = minions
            profile {
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
            .map { str -> str.uppercase(Locale.getDefault()) }.configure {
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
        private val logger = KotlinLogging.logger { }
    }
}
