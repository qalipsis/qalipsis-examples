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
import io.qalipsis.api.steps.filterNotNull
import io.qalipsis.api.steps.innerJoin
import io.qalipsis.api.steps.map
import io.qalipsis.api.steps.verify
import io.qalipsis.examples.utils.BatteryState
import io.qalipsis.examples.utils.BatteryStateContract
import io.qalipsis.examples.utils.DatabaseConfiguration
import io.qalipsis.examples.utils.DatabaseConfiguration.Companion.NUMBER_MINION
import io.qalipsis.plugins.jackson.csv.csvToObject
import io.qalipsis.plugins.jackson.jackson
import io.qalipsis.plugins.mongodb.Sorting
import io.qalipsis.plugins.mongodb.mongodb
import io.qalipsis.plugins.mongodb.poll.poll
import io.qalipsis.plugins.mongodb.save.save
import org.bson.Document
import java.time.Duration

@Suppress("DuplicatedCode")
class MongoDbSaveAndPoll {

    /**
     * help to parse from [BatteryState] to json and from json to [BatteryState]
     */
    private val objectMapper = ObjectMapper().also {
        it.registerModule(JavaTimeModule())
    }

    @Scenario("mongodb-save-and-poll")
    fun scenarioSaveAndPoll() {
        //we define the scenario, set the name, number of minions and rampUp
        scenario {
            minionsCount = NUMBER_MINION
            profile {
                regular(periodMs = 1000, minionsCountProLaunch = minionsCount)
            }
        }.start()
            .jackson() //we start the jackson step to fetch data from the csv file. we will use the csvToObject method to map csv entries to list of utils.BatteryState object
            .csvToObject(BatteryState::class) {
                name = "csv to json"

                classpath("battery-levels.csv")
                // we define the header of the csv file
                header {
                    column("deviceId")
                    column("timestamp")
                    column("batteryLevel").integer()
                }
                unicast()
            }.map { it.value } // we transform the output of the CSV reader entries to utils.BatteryState
            .mongodb() // we start the mongodb step to save data in mongodb database
            .save {
                name = "save"

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
            .map{it.input}
            .innerJoin(
                using = { correlationRecord -> correlationRecord.value.primaryKey },
                on = {
                    it.mongodb().poll {
                        name = "poll"

                        connect {
                            MongoClients.create(DatabaseConfiguration.SERVER_LINK)
                        }

                        search {
                            database = DatabaseConfiguration.DATABASE
                            collection = DatabaseConfiguration.COLLECTION
                            query = Document()
                            sort = linkedMapOf(BatteryStateContract.DEVICE_ID to Sorting.ASC)
                            tieBreaker = BatteryStateContract.DEVICE_ID
                        }

                        pollDelay(Duration.ofSeconds(1)) //we pull the database after every on second

                    }.flatten().map { record ->
                        objectMapper.convertValue(record.value, BatteryState::class.java)
                    }
                }, having = { correlationRecord ->
                    correlationRecord.value.primaryKey
                })
            .filterNotNull()
            .verify { result ->
                result.asClue {
                    assertSoftly {
                        it.first.batteryLevel shouldBeExactly it.second.batteryLevel
                    }
                }
            }
    }

}