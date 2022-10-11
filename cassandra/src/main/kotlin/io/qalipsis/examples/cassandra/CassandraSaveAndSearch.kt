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
import io.kotest.matchers.ints.shouldBeExactly
import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.executionprofile.regular
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.map
import io.qalipsis.api.steps.verify
import io.qalipsis.examples.utils.BatteryState
import io.qalipsis.examples.utils.BatteryStateContract
import io.qalipsis.examples.utils.DatabaseConfiguration.DATACENTER_NAME
import io.qalipsis.examples.utils.DatabaseConfiguration.KEYSPACE
import io.qalipsis.examples.utils.DatabaseConfiguration.SERVERS
import io.qalipsis.examples.utils.DatabaseConfiguration.TABLE_NAME
import io.qalipsis.examples.utils.ScenarioConfiguration.NUMBER_MINION
import io.qalipsis.plugins.cassandra.cassandra
import io.qalipsis.plugins.cassandra.save.CassandraSaveRow
import io.qalipsis.plugins.cassandra.save.save
import io.qalipsis.plugins.cassandra.search.search
import io.qalipsis.plugins.jackson.csv.csvToObject
import io.qalipsis.plugins.jackson.jackson
import java.time.Instant

class CassandraSaveAndSearch {

    @Scenario("cassandra-save-and-search")
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
            .cassandra() // we start the cassandra step to save data in cassandra database
            .save {
                name = "save"

                //setup connection of the database
                connect {
                    servers = SERVERS
                    keyspace = KEYSPACE
                    datacenterName = DATACENTER_NAME
                }

                //define the name of the database
                table { _, _ ->
                    TABLE_NAME
                }

                //define the name of columns of the table name
                columns { _, _ ->
                    listOf(
                        BatteryStateContract.COMPANY,
                        BatteryStateContract.DEVICE_ID,
                        BatteryStateContract.TIMESTAMP,
                        BatteryStateContract.BATTERY_LEVEL
                    )
                }

                //create the list of rows to save in database
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
            .search {
                name = "search"

                connect {
                    servers = SERVERS
                    keyspace = KEYSPACE
                    datacenterName = DATACENTER_NAME
                }
                query { _, _ ->
                    "SELECT * FROM $TABLE_NAME WHERE ${BatteryStateContract.COMPANY} = ? AND ${BatteryStateContract.DEVICE_ID} = ? AND ${BatteryStateContract.TIMESTAMP} = ?"
                }
                parameters { _, input ->
                    listOf("ACME Inc.", input.input.deviceId, input.input.timestamp)
                }
            }
            .map {
                it.input.input to it.records.first().let { cassandraRecord ->
                    BatteryState(
                        deviceId = cassandraRecord.value[CqlIdentifier.fromCql(BatteryStateContract.DEVICE_ID)] as String,
                        timestamp = cassandraRecord.value[CqlIdentifier.fromCql(BatteryStateContract.TIMESTAMP)] as Instant,
                        batteryLevel = (cassandraRecord.value[CqlIdentifier.fromCql(BatteryStateContract.BATTERY_LEVEL)] as Number).toInt()
                    )
                }
            }
            .verify { result ->
                val savedBatteryState = result.first
                val foundedBatteryState = result.second
                foundedBatteryState.batteryLevel shouldBeExactly savedBatteryState.batteryLevel
            }
    }

}