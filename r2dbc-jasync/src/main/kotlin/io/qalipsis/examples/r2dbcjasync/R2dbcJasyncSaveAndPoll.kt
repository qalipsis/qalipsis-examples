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

package io.qalipsis.examples.r2dbcjasync

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
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
import io.qalipsis.examples.utils.DatabaseConfiguration.PostgresDatabaseConfiguration
import io.qalipsis.examples.utils.ScenarioConfiguration
import io.qalipsis.plugins.jackson.csv.csvToObject
import io.qalipsis.plugins.jackson.jackson
import io.qalipsis.plugins.r2dbc.jasync.dialect.Protocol
import io.qalipsis.plugins.r2dbc.jasync.poll.poll
import io.qalipsis.plugins.r2dbc.jasync.r2dbcJasync
import io.qalipsis.plugins.r2dbc.jasync.save.JasyncSaveRecord
import io.qalipsis.plugins.r2dbc.jasync.save.save
import java.time.Duration

class R2dbcJasyncSaveAndPoll {

    /**
     * help to parse from [BatteryStateDesiarialise] to json and from json to [BatteryStateDesiarialise]
     */
    private val objectMapper = ObjectMapper().also {
        it.registerModule(JavaTimeModule())
    }

    private val databaseConfiguration = PostgresDatabaseConfiguration()

    @Scenario("r2dbc-jasync-save-and-poll")
    fun r2dbcJasyncSaveAndPoll() {

        scenario {
            minionsCount = ScenarioConfiguration.NUMBER_MINION
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
                    column("device_id")
                    column("timestamp")
                    column("battery_level").integer()
                }
                unicast()
            }
            .map { it.value }
            .r2dbcJasync()// we transform the output of the CSV reader entries to utils.BatteryState
            .save {

                protocol(Protocol.POSTGRESQL)

                connection {
                    database = databaseConfiguration.databaseName
                    port = databaseConfiguration.port
                    username = databaseConfiguration.userName
                    password = databaseConfiguration.password
                }

                tableName { _, _ ->
                    databaseConfiguration.tableName
                }

                columns { _, _ ->
                    listOf(
                        BatteryStateContract.ID,
                        BatteryStateContract.DEVICE_ID,
                        BatteryStateContract.TIMESTAMP,
                        BatteryStateContract.BATTERY_LEVEL
                    )
                }

                values { _, input ->
                    listOf(
                        JasyncSaveRecord(
                            input.primaryKey(),
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
            .innerJoin(
                using = { correlationRecord ->
                    correlationRecord.value.primaryKey()
                },
                on = {
                    it.r2dbcJasync().poll {

                        protocol(Protocol.POSTGRESQL)

                        connection {
                            database = databaseConfiguration.databaseName
                            port = databaseConfiguration.port
                            username = databaseConfiguration.userName
                            password = databaseConfiguration.password
                        }

                        query("select * from ${databaseConfiguration.tableName} order by \"${BatteryStateContract.TIMESTAMP}\"")

                        pollDelay(Duration.ofSeconds(1))
                    }
                        .flatten()
                        .map { record ->
                            objectMapper.convertValue(record.value, BatteryState::class.java)
                        }
                },
                having = { correlationRecord ->
                    correlationRecord.value.primaryKey()
                }
            )
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