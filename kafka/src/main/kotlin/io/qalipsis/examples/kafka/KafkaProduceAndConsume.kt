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

package io.qalipsis.examples.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.executionprofile.regular
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.filterNotNull
import io.qalipsis.api.steps.innerJoin
import io.qalipsis.api.steps.map
import io.qalipsis.examples.utils.BatteryState
import io.qalipsis.examples.utils.ServerConfiguration
import io.qalipsis.plugins.jackson.csv.csvToObject
import io.qalipsis.plugins.jackson.jackson
import io.qalipsis.plugins.kafka.consumer.consume
import io.qalipsis.plugins.kafka.kafka
import io.qalipsis.plugins.kafka.producer.KafkaProducerRecord
import io.qalipsis.plugins.kafka.producer.produce
import io.qalipsis.plugins.kafka.serdes.jsonSerde
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.common.serialization.Serdes
import java.time.Duration

@Suppress("DuplicatedCode")
class KafkaProduceAndConsume {

    private val objectMapper = ObjectMapper().also {
        it.registerModule(JavaTimeModule())
    }

    @Scenario("kafka-produce-and-consume")
    fun scenarioSaveAndPoll() {

        //we define the scenario, set the name, number of minions and rampUp
        scenario {
            minionsCount = ServerConfiguration.NUMBER_MINION
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
            .kafka()
            .produce(
                keySerializer = Serdes.String().serializer(),
                valueSerializer = Serdes.String().serializer()
            ) {
                bootstrap(ServerConfiguration.SERVER_BOOTSTRAP)
                clientName("producer")
                records { _, input ->
                    listOf(
                        KafkaProducerRecord(
                            ServerConfiguration.TOPIC,
                            value = objectMapper.writeValueAsString(input),
                            key = input.primaryKey
                        )
                    )
                }
            }
            .map { it.input }
            .innerJoin(
                using = { correlationRecord ->
                    correlationRecord.value.primaryKey
                },
                on = {
                    it.kafka().consume {
                        bootstrap(ServerConfiguration.SERVER_BOOTSTRAP)
                        topics(ServerConfiguration.TOPIC) //Define which topic we want to listen
                        groupId("kafka-example")
                        offsetReset(OffsetResetStrategy.EARLIEST) //where we want to start consume. EARLIEST starts consuming messages from the beginning of the queue
                        pollTimeout(Duration.ofSeconds(1))
                    }
                        .flatten(Serdes.String().deserializer(), jsonSerde<BatteryState>().deserializer())
                        .map { result ->
                            result.record.value
                        }
                        .filterNotNull()
                },
                having = { correlationRecord ->
                    correlationRecord.value.primaryKey
                }
            )
    }

}