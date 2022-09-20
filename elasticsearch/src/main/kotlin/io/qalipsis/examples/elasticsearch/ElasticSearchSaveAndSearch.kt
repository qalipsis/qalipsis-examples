package io.qalipsis.examples.elasticsearch

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.kotest.assertions.asClue
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.executionprofile.regular
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.delay
import io.qalipsis.api.steps.map
import io.qalipsis.api.steps.verify
import io.qalipsis.examples.utils.BatteryState
import io.qalipsis.examples.utils.BatteryStateContract
import io.qalipsis.examples.utils.ScenarioConfiguration.Companion.NUMBER_MINION
import io.qalipsis.plugins.elasticsearch.Document
import io.qalipsis.plugins.elasticsearch.elasticsearch
import io.qalipsis.plugins.elasticsearch.save.save
import io.qalipsis.plugins.elasticsearch.search.search
import io.qalipsis.plugins.jackson.csv.csvToObject
import io.qalipsis.plugins.jackson.jackson
import org.apache.http.HttpHost
import org.elasticsearch.client.RestClient
import java.time.Duration

class ElasticSearchSaveAndSearch {

    private val objectMapper = ObjectMapper().also {
        it.registerModule(JavaTimeModule())
    }

    @Scenario("elasticsearch-save-and-search")
    fun scenarioSaveAndSearch() {
        scenario {
            minionsCount = NUMBER_MINION
            profile {
                regular(periodMs = 1000, minionsCountProLaunch = minionsCount)
            }
        }
            .start()
            .jackson() //we start the jackson step to fetch data from the csv file. we will use the csvToObject method to map csv entries to list of utils.BatteryState object
            .csvToObject(BatteryState::class) {

                classpath("battery-levels.csv")
                // we define the header of the csv file
                header {
                    column("deviceId")
                    column("timestamp")
                    column("batteryLevel").integer()
                }
                unicast()
            }
            .map { it.value } // we transform the output of the CSV reader entries to utils.BatteryState
            .elasticsearch()
            .save {
                name = "save"
                client {
                    RestClient.builder(HttpHost.create("http://localhost:9200"))
                        .build() // we create the rest client that will help to submit request to elastic search
                }
                documents { _, input ->
                    listOf(
                        Document(
                            index = "battery-state",
                            id = input.primaryKey,
                            source = objectMapper.writeValueAsString(input) // we parse to json the object we want to save
                        )
                    )
                }
            }
            .delay(Duration.ofSeconds(2)) // a small delay of 2 seconds before proceed, because on elastic search persistence of data is not immediate
            .elasticsearch()
            .search {
                name = "search"
                client {
                    RestClient.builder(HttpHost.create("http://localhost:9200")).build()
                }
                index { _, _ -> listOf(BatteryStateContract.INDEX) } // we specify the table where we want to fetch data
                query { _, input ->
                    """{
                        "query": {
                            "bool": {
                                "must": [
                                    { "match": { "deviceId": "${input.input.deviceId}" }},
                                    { "term": { "timestamp": ${input.input.timestamp.epochSecond} }}
                                ]
                            }
                        }
                   }"""
                } // we create the query that will help us get battery state of a specific id and specific timestamp
            }
            .deserialize(BatteryState::class) // use this method to transform your elastic search result to a specific object of your class
            .verify { result ->
                val savedBatteryState = result.first.input
                result.second.asClue {
                    assertSoftly {
                        it.results shouldHaveSize 1
                        it.results.first().value.batteryLevel shouldBe savedBatteryState.batteryLevel
                    }
                }
            }

    }

}