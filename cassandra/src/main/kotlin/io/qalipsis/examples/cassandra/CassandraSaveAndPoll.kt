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

package io.qalipsis.examples.cassandra

import com.datastax.oss.driver.api.core.CqlIdentifier
import com.datastax.oss.driver.api.core.type.reflect.GenericType
import io.kotest.matchers.ints.shouldBeExactly
import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.executionprofile.immediate
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.filterNotNull
import io.qalipsis.api.steps.innerJoin
import io.qalipsis.api.steps.map
import io.qalipsis.api.steps.verify
import io.qalipsis.plugins.cassandra.cassandra
import io.qalipsis.plugins.cassandra.poll.poll
import io.qalipsis.plugins.cassandra.save.CassandraSaveRow
import io.qalipsis.plugins.cassandra.save.save
import io.qalipsis.plugins.jackson.csv.csvToObject
import io.qalipsis.plugins.jackson.jackson
import java.time.Duration
import java.time.Instant

@Suppress("DuplicatedCode")
class CassandraSaveAndPoll {

    @Scenario("cassandra-save-and-poll")
    fun scenarioSaveAndPoll() {

        // we define the scenario, set the name, number of minions and rampUp
        scenario {
            minionsCount = 20
            profile {
                immediate()
            }
        }
            .start()
            .jackson() // we start the jackson step to fetch data from the csv file. we will use the csvToObject method to map csv entries to list of utils.BatteryState object
            .csvToObject(mappingClass = BatteryState::class) {

                classpath(path = "battery-levels.csv")
                // we define the header of the csv file
                header {
                    column(name = "deviceId")
                    column(name = "timestamp")
                    column(name = "batteryLevel").integer()
                }
                unicast()
            }
            .map { it.value } // we transform the output of the CSV reader entries to utils.BatteryState
            .cassandra() // we start the cassandra step to save data in cassandra database
            .save {

                // setup connection of the database
                connect {
                    servers = listOf("localhost:9042")
                    keyspace = "iot"
                    datacenterName = "datacenter1"
                }

                // define the name of the database
                table { _, _ ->
                    "batteryState"
                }

                // define the name of columns of the table name
                columns { _, _ ->
                    listOf(
                        "company",
                        "deviceid",
                        "timestamp",
                        "batterylevel"
                    )
                }

                // create the list of rows to save in database
                rows { _, input ->
                    listOf(
                        CassandraSaveRow(
                            "'ACME Inc.'",
                            "'${input.deviceId}'",
                            "'${input.timestamp}'",
                            input.batteryLevel
                        )
                    )
                }
            }
            .map { it.input }
            .innerJoin()
            .using { correlationRecord -> correlationRecord.value.deviceId }
            .on {
                it.cassandra().poll {
                    connect {
                        servers = listOf("localhost:9042")
                        keyspace = "iot"
                        datacenterName = "datacenter1"
                    }
                    query(queryString = "SELECT deviceid, timestamp, batterylevel FROM batteryState WHERE company = ? ORDER BY timestamp")
                    parameters("ACME Inc.")
                    tieBreaker {
                        name = "timestamp"
                        type = GenericType.INSTANT
                    }
                    pollDelay(duration = Duration.ofSeconds(1))
                }
                    .flatten()
                    .map { record ->
                        BatteryState(
                            deviceId = record.value[CqlIdentifier.fromCql("deviceid")] as String,
                            timestamp = record.value[CqlIdentifier.fromCql("timestamp")] as Instant,
                            batteryLevel = (record.value[CqlIdentifier.fromCql("batterylevel")] as Number).toInt()
                        )
                    }
            }
            .having { correlationRecord ->
                correlationRecord.value.deviceId
            }
            .configure {
                timeout(duration = 5000L)
                report {
                    reportErrors = true
                }
            }
            .filterNotNull()
            .verify { result ->
                val savedBatteryState = result.first
                val foundedBatteryState = result.second
                foundedBatteryState.batteryLevel shouldBeExactly savedBatteryState.batteryLevel
            }
    }

}