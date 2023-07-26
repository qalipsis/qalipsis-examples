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
import io.qalipsis.api.steps.*
import io.qalipsis.examples.utils.BatteryState
import io.qalipsis.examples.utils.BatteryStateContract
import io.qalipsis.examples.utils.ScenarioConfiguration
import io.qalipsis.examples.utils.ServerConfiguration
import io.qalipsis.plugins.jackson.csv.csvToObject
import io.qalipsis.plugins.jackson.jackson
import io.qalipsis.plugins.redis.lettuce.configuration.RedisConnectionType
import io.qalipsis.plugins.redis.lettuce.redisLettuce
import io.qalipsis.plugins.redis.lettuce.streams.consumer.LettuceStreamsConsumerOffset
import io.qalipsis.plugins.redis.lettuce.streams.consumer.streamsConsume
import io.qalipsis.plugins.redis.lettuce.streams.producer.LettuceStreamsProduceRecord
import io.qalipsis.plugins.redis.lettuce.streams.producer.streamsProduce

class RedisProduceAndConsume {

    private val objectMapper = ObjectMapper().also {
        it.registerModule(JavaTimeModule())
    }

    @Scenario("redis-produce-and-consume")
    fun redisProduceAndConsume() {

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
            .streamsProduce {
                connection {
                    nodes = ServerConfiguration.NODES
                    database = ServerConfiguration.DATABASE
                    redisConnectionType = RedisConnectionType.SINGLE
                    authPassword = ServerConfiguration.PASSWORD
                    authUser = ServerConfiguration.USER_NAME
                }

                records { _, input ->
                    listOf(
                        LettuceStreamsProduceRecord(
                            key = BatteryStateContract.KEY_FOR_PRODUCE_AND_CONSUME, value = mapOf(
                                BatteryStateContract.DEVICE_ID to input.deviceId,
                                BatteryStateContract.TIMESTAMP to input.timestamp.epochSecond.toString(),
                                BatteryStateContract.BATTERY_LEVEL to input.batteryLevel.toString()
                            )
                        )
                    )
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
                    it.redisLettuce()
                        .streamsConsume {
                            connection {
                                nodes = ServerConfiguration.NODES
                                database = ServerConfiguration.DATABASE
                                redisConnectionType = RedisConnectionType.SINGLE
                                authPassword = ServerConfiguration.PASSWORD
                                authUser = ServerConfiguration.USER_NAME
                            }

                            streamKey(BatteryStateContract.KEY_FOR_PRODUCE_AND_CONSUME)
                            offset(LettuceStreamsConsumerOffset.FROM_BEGINNING)
                            group("consumer")
                        }
                        .flatten()
                        .map { result ->
                            objectMapper.convertValue(result.value,BatteryState::class.java)
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