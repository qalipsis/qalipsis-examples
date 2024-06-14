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

package io.qalipsis.examples.graphite

import io.kotest.assertions.asClue
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.ints.shouldBeExactly
import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.executionprofile.immediately
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.delay
import io.qalipsis.api.steps.filterNotNull
import io.qalipsis.api.steps.flatten
import io.qalipsis.api.steps.innerJoin
import io.qalipsis.api.steps.map
import io.qalipsis.api.steps.verify
import io.qalipsis.plugins.graphite.client.GraphiteRecord
import io.qalipsis.plugins.graphite.graphite
import io.qalipsis.plugins.graphite.poll.poll
import io.qalipsis.plugins.graphite.save.save
import io.qalipsis.plugins.graphite.search.GraphiteMetricsTime
import io.qalipsis.plugins.graphite.search.GraphiteMetricsTimeUnit
import io.qalipsis.plugins.graphite.search.GraphiteQuery
import io.qalipsis.plugins.jackson.csv.csvToObject
import io.qalipsis.plugins.jackson.jackson
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class GraphiteSaveAndPoll {

    private val yesterday = LocalDate.now().minusDays(1)

    private val localZoneOffset = ZoneId.systemDefault().rules.getOffset(LocalDateTime.now())

    @Scenario("graphite-save-and-poll")
    fun scenarioSaveAndPoll() {

        scenario {
            minionsCount = 20
            profile {
                immediately()
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
            .map {
                // Graphite generates data points for all the missing granular periods.
                // So we force the timestamp to a recent date to avoid having too many of them.
                val timeReportedToToday = LocalTime.from(it.value.timestamp.atZone(ZoneId.systemDefault()))
                val deviceTimestampReportedToToday =
                    yesterday.atTime(timeReportedToToday.hour, timeReportedToToday.minute, timeReportedToToday.second)

                it.value.copy(timestamp = deviceTimestampReportedToToday.toInstant(localZoneOffset))
            }
            .graphite()// we transform the output of the CSV reader entries to utils.BatteryState
            .save {

                connect {
                    server("localhost", 2003)
                }

                records { _, input ->
                    listOf(
                        GraphiteRecord(
                            metricPath = "device.battery-state.${input.deviceId.lowercase()}",
                            value = input.batteryLevel,
                            timestamp = input.timestamp
                        )
                    )
                }

                monitoring {
                    events = false
                    meters = true
                }

            }
            .delay(Duration.ofSeconds(1))
            .map { it.input }
            .innerJoin()
            .using { correlationRecord ->
                correlationRecord.value.deviceId
            }
            .on {
                it.graphite().poll {
                    name = "my-poll-step"

                    connect {
                        server("http://localhost:8080")
                    }

                    monitoring {
                        events = false
                        meters = true
                    }

                    query {
                        GraphiteQuery("device.battery-state.*")
                            // By default, Graphite provides only the latest 24 hours.
                            .from(GraphiteMetricsTime(-2, GraphiteMetricsTimeUnit.DAYS))
                            .noNullPoints(true)
                    }
                    broadcast(123, Duration.ofSeconds(20))
                    pollDelay(Duration.ofSeconds(1))
                }
                    .flatten()
                    .map { record ->
                        val dataPoint = record.dataPoints.first()
                        BatteryState(
                            deviceId = record.target.substringAfter("device.battery-state.").uppercase(),
                            batteryLevel = dataPoint.value.toInt(),
                            timestamp = dataPoint.timestamp
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