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
import io.qalipsis.examples.utils.BatteryState
import io.qalipsis.examples.utils.BatteryStateContract
import io.qalipsis.examples.utils.DatabaseConfiguration
import io.qalipsis.examples.utils.ScenarioConfiguration.NUMBER_MINION
import io.qalipsis.plugins.influxdb.influxdb
import io.qalipsis.plugins.influxdb.save.save
import io.qalipsis.plugins.influxdb.search.search
import io.qalipsis.plugins.jackson.csv.csvToObject
import io.qalipsis.plugins.jackson.jackson
import java.time.Instant

class InfluxdbSaveAndSearch {
    @Scenario("influxdb-save-and-search")
    fun elasticSearchSaveAndPoll() {

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
            .influxdb()
            .save {
                connect {
                    server(
                        url = DatabaseConfiguration.SERVER_URL,
                        bucket = DatabaseConfiguration.BUCKET,
                        org = DatabaseConfiguration.ORGANISATION
                    )
                    basic(user = DatabaseConfiguration.USER_NAME, password = DatabaseConfiguration.PASSWORD)
                }

                query {
                    bucket = { _, _ -> DatabaseConfiguration.BUCKET }

                    organization = { _, _ -> DatabaseConfiguration.ORGANISATION }

                    points = { _, input ->
                        listOf(
                            Point.measurement(BatteryStateContract.MEASUREMENT)
                                .addField(BatteryStateContract.BATTERY_LEVEL, input.batteryLevel)
                                .addTag(BatteryStateContract.DEVICE_ID, input.deviceId)
                                .addTag(BatteryStateContract.TIMESTAMP, input.timestamp.epochSecond.toString())
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
                        url = DatabaseConfiguration.SERVER_URL,
                        bucket = DatabaseConfiguration.BUCKET,
                        org = DatabaseConfiguration.ORGANISATION
                    )
                    basic(user = DatabaseConfiguration.USER_NAME, password = DatabaseConfiguration.PASSWORD)
                }

                query { _, input ->
                    """
                        from(bucket: "${DatabaseConfiguration.BUCKET}")
                                |> range(start: -15m)
                                |> filter(
                                    fn: (r) => r._measurement == "${BatteryStateContract.MEASUREMENT}" and
                                        r.${BatteryStateContract.DEVICE_ID} == "${input.deviceId}" and
                                        r.${BatteryStateContract.TIMESTAMP} == "${input.timestamp.epochSecond}"
                                    )               
                    """.trimIndent()
                }

            }
            .map {
                it.input to it.results.map { fluxRecord ->
                    BatteryState(
                        deviceId = fluxRecord.values[BatteryStateContract.DEVICE_ID] as String,
                        timestamp = Instant.ofEpochSecond((fluxRecord.values[BatteryStateContract.TIMESTAMP] as String).toLong()),
                        batteryLevel = (fluxRecord.value as Number).toInt()
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