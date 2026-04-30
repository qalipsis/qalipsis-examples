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

package io.qalipsis.examples.sql

import io.kotest.assertions.asClue
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.ints.shouldBeExactly
import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.executionprofile.immediate
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.filterNotNull
import io.qalipsis.api.steps.innerJoin
import io.qalipsis.api.steps.map
import io.qalipsis.api.steps.verify
import io.qalipsis.plugins.jackson.csv.csvToObject
import io.qalipsis.plugins.jackson.jackson
import io.qalipsis.plugins.sql.dialect.Protocol
import io.qalipsis.plugins.sql.poll.poll
import io.qalipsis.plugins.sql.save.SqlSaveRecord
import io.qalipsis.plugins.sql.save.save
import io.qalipsis.plugins.sql.sql
import java.time.Duration
import java.time.Instant

class SqlSaveAndPoll {

    @Scenario("sql-save-and-poll")
    fun sqlSaveAndPoll() {

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
                    column(name = "device_id")
                    column(name = "timestamp")
                    column(name = "battery_level").integer()
                }
                unicast()
            }
            .map { it.value }
            .sql()// we transform the output of the CSV reader entries to utils.BatteryState
            .save {

                protocol(Protocol.POSTGRESQL)

                connection {
                    database = "postgres"
                    port = 15432
                    username = "postgres"
                    password = "root"
                }

                tableName { _, _ ->
                    "battery_state"
                }

                columns { _, _ ->
                    listOf(
                        "id",
                        "device_id",
                        "timestamp",
                        "battery_level"
                    )
                }

                values { _, input ->
                    listOf(
                        SqlSaveRecord(
                            input.deviceId,
                            input.deviceId,
                            input.timestamp.epochSecond,
                            input.batteryLevel
                        )
                    )
                }

                monitoring {
                    events = false
                    meters = true
                }

            }
            .map {
                it.input
            }
            .innerJoin()
            .using { correlationRecord ->
                correlationRecord.value.deviceId
            }
            .on {
                it.sql().poll {

                    protocol(Protocol.POSTGRESQL)

                    connection {
                        database = "postgres"
                        port = 15432
                        username = "postgres"
                        password = "root"
                    }

                    query("select * from battery_state order by \"timestamp\"")

                    pollDelay(Duration.ofSeconds(1))
                }
                    .flatten()
                    .map { record ->
                        val batteryState = record.value
                        BatteryState(
                            deviceId = batteryState.getValue("device_id") as String,
                            batteryLevel = batteryState.getValue("battery_level") as Int,
                            timestamp = Instant.ofEpochSecond((batteryState.getValue("timestamp") as Int).toLong())
                        )
                    }
            }
            .having { correlationRecord ->
                correlationRecord.value.deviceId
            }
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