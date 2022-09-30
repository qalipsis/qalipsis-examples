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

package io.qalipsis.examples.mongodb

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.mongodb.reactivestreams.client.MongoClients
import io.kotest.assertions.asClue
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.ints.shouldBeExactly
import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.executionprofile.regular
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.map
import io.qalipsis.api.steps.verify
import io.qalipsis.examples.utils.BatteryState
import io.qalipsis.examples.utils.BatteryStateContract
import io.qalipsis.examples.utils.DatabaseConfiguration
import io.qalipsis.examples.utils.DatabaseConfiguration.Companion.NUMBER_MINION
import io.qalipsis.plugins.jackson.csv.csvToObject
import io.qalipsis.plugins.jackson.jackson
import io.qalipsis.plugins.mongodb.mongodb
import io.qalipsis.plugins.mongodb.save.save
import io.qalipsis.plugins.mongodb.search.search
import org.bson.Document

class MongoDbSaveAndSearch {

    private val objectMapper = ObjectMapper().also {
        it.registerModule(JavaTimeModule())
    }

    @Scenario("mongodb-save-and-search")
    fun scenarioSaveAndSearch() {
        //we define the scenario, set the name, number of minions and rampUp
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
            .mongodb()// we start the mongodb step to save data in mongodb database
            .save {

                //setup connection of the database
                connect {
                    MongoClients.create(DatabaseConfiguration.SERVER_LINK)
                }

                query {
                    database { _, _ ->
                        DatabaseConfiguration.DATABASE
                    }

                    collection { _, _ ->
                        DatabaseConfiguration.COLLECTION
                    }

                    documents { _, input ->
                        listOf(Document.parse(objectMapper.writeValueAsString(input)))
                    }
                }
            }
            .map { it.input }
            .mongodb()
            .search {
                name = "search"

                connect {
                    MongoClients.create(DatabaseConfiguration.SERVER_LINK)
                }

                search {
                    database { _, _ -> DatabaseConfiguration.DATABASE }
                    collection { _, _ -> DatabaseConfiguration.COLLECTION }
                    query { _, input ->
                        Document(
                            mapOf(
                                BatteryStateContract.DEVICE_ID to input.deviceId,
                                BatteryStateContract.TIMESTAMP to input.timestamp.epochSecond
                            )
                        )
                    }
                }
            }
            .map {
                it.input to it.documents.map { document ->
                    objectMapper.readValue(document.toJson(), BatteryState::class.java)
                }
            }
            .verify { result ->
                result.asClue {
                    assertSoftly {
                        it.second.size shouldBeExactly 1
                        it.first.batteryLevel shouldBeExactly it.second.first().batteryLevel
                    }
                }
            }
    }

}