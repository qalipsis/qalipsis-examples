package io.qalipsis.examples.r2dbcjasync

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
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
import io.qalipsis.examples.utils.DatabaseConfiguration.MariaDBDatabaseConfiguration
import io.qalipsis.examples.utils.ScenarioConfiguration
import io.qalipsis.plugins.jackson.csv.csvToObject
import io.qalipsis.plugins.jackson.jackson
import io.qalipsis.plugins.r2dbc.jasync.dialect.Protocol
import io.qalipsis.plugins.r2dbc.jasync.r2dbcJasync
import io.qalipsis.plugins.r2dbc.jasync.save.JasyncSaveRecord
import io.qalipsis.plugins.r2dbc.jasync.save.save
import io.qalipsis.plugins.r2dbc.jasync.search.search

class R2dbcJasyncSaveAndSearch {

    /**
     * help to parse from [BatteryStateDesiarialise] to json and from json to [BatteryStateDesiarialise]
     */
    private val objectMapper = ObjectMapper().also {
        it.registerModule(JavaTimeModule())
    }

    private val databaseConfiguration = MariaDBDatabaseConfiguration()

    @Scenario("r2dbc-jasync-save-and-search")
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

                protocol(Protocol.MARIADB)

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
            .map { it.input }
            .r2dbcJasync()
            .search {
                protocol(Protocol.MARIADB)

                connection {
                    database = databaseConfiguration.databaseName
                    port = databaseConfiguration.port
                    username = databaseConfiguration.userName
                    password = databaseConfiguration.password
                }

                query { _, _ ->
                    val request = "SELECT DISTINCT * from ${databaseConfiguration.tableName} where ${BatteryStateContract.TIMESTAMP} = ? AND ${BatteryStateContract.DEVICE_ID} = ?"
                    request
                }
                parameters { _, input ->
                    listOf(input.timestamp.epochSecond, input.deviceId)
                }
            }
            .map {
                it.input to it.records.map { record ->
                    objectMapper.convertValue(
                        record.value,
                        BatteryState::class.java
                    )
                }
            }
            .verify { result ->
                result.asClue {
                    assertSoftly {
                        result.second.size shouldBeExactly 1
                        result.first.batteryLevel shouldBeExactly result.second.first().batteryLevel
                    }
                }
            }

    }

}