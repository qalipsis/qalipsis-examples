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

package io.qalipsis.examples.redis

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
import io.qalipsis.examples.utils.ScenarioConfiguration
import io.qalipsis.examples.utils.ServerConfiguration
import io.qalipsis.plugins.jackson.csv.csvToObject
import io.qalipsis.plugins.jackson.jackson
import io.qalipsis.plugins.redis.lettuce.configuration.RedisConnectionType
import io.qalipsis.plugins.redis.lettuce.poll.pollSscan
import io.qalipsis.plugins.redis.lettuce.redisLettuce
import io.qalipsis.plugins.redis.lettuce.save.records.SetRecord
import io.qalipsis.plugins.redis.lettuce.save.save
import java.time.Duration

class RedisSaveAndPoll {

    private val objectMapper = ObjectMapper().also {
        it.registerModule(JavaTimeModule())
    }

    @Scenario("redis-save-and-poll")
    fun redisSaveAndPoll() {

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
            .redisLettuce()
            .save {
                connection {
                    nodes = ServerConfiguration.NODES
                    database = ServerConfiguration.DATABASE
                    redisConnectionType = RedisConnectionType.SINGLE
                    authPassword = ServerConfiguration.PASSWORD
                    authUser = ServerConfiguration.USER_NAME
                }

                records { _, input ->
                    listOf(
                        SetRecord(BatteryStateContract.KEY_FOR_SAVE_AND_POLL,
                            objectMapper.writeValueAsString(input)
                        )
                    )
                }
            }
            .map{
                it.input
            }
            .innerJoin(
                using = {correlationRecord -> correlationRecord.value.primaryKey() },

                on = {
                    it.redisLettuce()
                        .pollSscan {
                            connection {
                                nodes = ServerConfiguration.NODES
                                database = ServerConfiguration.DATABASE
                                redisConnectionType = RedisConnectionType.SINGLE
                                authPassword = ServerConfiguration.PASSWORD
                                authUser = ServerConfiguration.USER_NAME
                            }

                            keyOrPattern(BatteryStateContract.KEY_FOR_SAVE_AND_POLL)

                            pollDelay(Duration.ofSeconds(1))
                        }
                        .flatten()
                        .map{ result ->
                            val batteryState = objectMapper.readValue(result.value,BatteryState::class.java)
                            batteryState
                        }
                },

                having = {correlationRecord -> correlationRecord.value.primaryKey()}
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