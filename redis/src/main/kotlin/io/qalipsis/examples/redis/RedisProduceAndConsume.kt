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
import io.qalipsis.plugins.redis.lettuce.configuration.RedisConnectionType
import io.qalipsis.plugins.redis.lettuce.redisLettuce
import io.qalipsis.plugins.redis.lettuce.streams.consumer.LettuceStreamsConsumerOffset
import io.qalipsis.plugins.redis.lettuce.streams.consumer.streamsConsume
import io.qalipsis.plugins.redis.lettuce.streams.producer.LettuceStreamsProduceRecord
import io.qalipsis.plugins.redis.lettuce.streams.producer.streamsProduce
import java.time.Instant

class RedisProduceAndConsume {

    @Scenario("redis-produce-and-consume")
    fun redisProduceAndConsume() {

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
            .redisLettuce()
            .streamsProduce {
                connection {
                    nodes = listOf("localhost:6379")
                    database = 0
                    redisConnectionType = RedisConnectionType.SINGLE
                    authPassword = ""
                    authUser = ""
                }

                records { _, input ->
                    listOf(
                        LettuceStreamsProduceRecord(
                            key = "battery_state_produce_and_consume", value = mapOf(
                                "device_id" to input.deviceId,
                                "timestamp" to input.timestamp.epochSecond.toString(),
                                "battery_level" to input.batteryLevel.toString()
                            )
                        )
                    )
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
                it.redisLettuce()
                    .streamsConsume {
                        connection {
                            nodes = listOf("localhost:6379")
                            database = 0
                            redisConnectionType = RedisConnectionType.SINGLE
                            authPassword = ""
                            authUser = ""
                        }

                        streamKey(key = "battery_state_produce_and_consume")
                        offset(LettuceStreamsConsumerOffset.FROM_BEGINNING)
                        group("consumer")
                    }
                    .flatten()
                    .map { result ->
                        val batteryState = result.value
                        BatteryState(
                            deviceId = batteryState.getValue("device_id"),
                            batteryLevel = batteryState.getValue("battery_level").toInt(),
                            timestamp = Instant.ofEpochSecond(batteryState.getValue("timestamp").toLong())
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