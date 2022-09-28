package io.qalipsis.examples.influxdb

import com.influxdb.client.write.Point
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
import io.qalipsis.examples.utils.ScenarioConfiguration.Companion.NUMBER_MINION
import io.qalipsis.plugins.influxdb.influxdb
import io.qalipsis.plugins.influxdb.poll.poll
import io.qalipsis.plugins.influxdb.save.save
import io.qalipsis.plugins.jackson.csv.csvToObject
import io.qalipsis.plugins.jackson.jackson
import java.time.Duration
import java.time.Instant

class InfluxdbSaveAndPoll {
    @Scenario("influxdb-save-and-poll")
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
            .map { it.input }
            .innerJoin(
                using = { correlationRecord ->
                    correlationRecord.value.primaryKey()
                },
                on = {
                    it.influxdb().poll {
                        connect {
                            server(
                                url = DatabaseConfiguration.SERVER_URL,
                                bucket = DatabaseConfiguration.BUCKET,
                                org = DatabaseConfiguration.ORGANISATION
                            )

                            basic(user = DatabaseConfiguration.USER_NAME, password = DatabaseConfiguration.PASSWORD)
                        }

                        query(
                            """
                            from(bucket: "${DatabaseConfiguration.BUCKET}")
                                |> range(start: -15m)
                                |> filter(
                                    fn: (r) => r._measurement == "${BatteryStateContract.MEASUREMENT}" 
                                    )               
                        """.trimIndent()
                        )

                        pollDelay(Duration.ofSeconds(1))

                    }
                        .flatten()
                        .map { fluxRecord ->
                            BatteryState(
                                deviceId = fluxRecord.values[BatteryStateContract.DEVICE_ID] as String,
                                timestamp = Instant.ofEpochSecond((fluxRecord.values[BatteryStateContract.TIMESTAMP] as String).toLong()),
                                batteryLevel = (fluxRecord.value as Number).toInt()
                            )
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