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

package io.qalipsis.examples.influxdb

import com.influxdb.client.write.Point
import io.kotest.assertions.asClue
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.ints.shouldBeExactly
import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.executionprofile.regular
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.map
import io.qalipsis.api.steps.verify
import io.qalipsis.plugins.influxdb.influxdb
import io.qalipsis.plugins.influxdb.save.save
import io.qalipsis.plugins.influxdb.search.search
import io.qalipsis.plugins.jackson.csv.csvToObject
import io.qalipsis.plugins.jackson.jackson
import java.time.Instant

class InfluxdbSaveAndSearch {
    @Scenario("influxdb-save-and-search")
    fun influxdbSearchSaveAndPoll() {

        scenario {
            minionsCount = 20
            profile {
                regular(periodMs = 1000, minionsCountProLaunch = minionsCount)
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
            .influxdb()
            .save {
                connect {
                    server(
                        url = "http://localhost:18086",
                        bucket = "iot",
                        org = "qalipsis"
                    )
                    basic(user = "qalipsis_user", password = "qalipsis_user_password")
                }

                query {
                    bucket = { _, _ -> "iot" }

                    organization = { _, _ -> "qalipsis" }

                    points = { _, input ->
                        listOf(
                            Point.measurement("battery_state")
                                .addField("battery_level", input.batteryLevel)
                                .addTag("device_id", input.deviceId)
                                .addTag("timestamp", input.timestamp.epochSecond.toString())
                        )
                    }

                }
            }
            .map {
                it.input
            }
            .influxdb()
            .search {

                connect {
                    server(
                        url = "http://localhost:18086",
                        bucket = "iot",
                        org = "qalipsis"
                    )
                    basic(user = "qalipsis_user", password = "qalipsis_user_password")
                }

                query { _, input ->
                    """
                        from(bucket: "iot")
                                |> range(start: -15m)
                                |> filter(
                                    fn: (r) => r._measurement == "battery_state" and
                                        r.device_id == "${input.deviceId}" and
                                        r.timestamp == "${input.timestamp.epochSecond}"
                                    )               
                    """.trimIndent()
                }

            }
            .map {
                it.input to it.results.map { fluxRecord ->
                    BatteryState(
                        deviceId = fluxRecord.values["device_id"] as String,
                        batteryLevel = (fluxRecord.value as Number).toInt(),
                        timestamp = Instant.ofEpochSecond((fluxRecord.values["timestamp"] as String).toLong())
                    )
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