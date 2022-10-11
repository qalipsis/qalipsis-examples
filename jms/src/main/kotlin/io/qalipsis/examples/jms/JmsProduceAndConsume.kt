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

package io.qalipsis.examples.jms

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
import io.qalipsis.examples.utils.ScenarioConfiguration
import io.qalipsis.examples.utils.ServerConfiguration
import io.qalipsis.plugins.jackson.csv.csvToObject
import io.qalipsis.plugins.jackson.jackson
import io.qalipsis.plugins.jms.consumer.consume
import io.qalipsis.plugins.jms.deserializer.JmsJsonDeserializer
import io.qalipsis.plugins.jms.jms
import io.qalipsis.plugins.jms.producer.JmsMessageType
import io.qalipsis.plugins.jms.producer.JmsProducerRecord
import io.qalipsis.plugins.jms.producer.produce
import org.apache.activemq.ActiveMQConnectionFactory
import org.apache.activemq.command.ActiveMQQueue

class JmsProduceAndConsume {

    private val objectMapper = ObjectMapper().also {
        it.registerModule(JavaTimeModule())
    }

    @Scenario("jms-produce-and-consume")
    fun scenarioProduceAndConsume() {

        //we define the scenario, set the name, number of minions and rampUp
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
                    column("deviceId")
                    column("timestamp")
                    column("batteryLevel").integer()
                }
                unicast()
            }
            .map { it.value } // we transform the output of the CSV reader entries to utils.BatteryState
            .jms()
            .produce {

                connect {
                    ActiveMQConnectionFactory(ServerConfiguration.SERVER_URL).createConnection()
                }

                records { _, input ->
                    listOf(
                        JmsProducerRecord(
                            destination = ActiveMQQueue().createDestination(ServerConfiguration.QUEUE_NAME),
                            messageType = JmsMessageType.BYTES,
                            value = objectMapper.writeValueAsBytes(input)
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
                    it.jms().consume {
                        queues(ServerConfiguration.QUEUE_NAME)
                        queueConnection { ActiveMQConnectionFactory(ServerConfiguration.SERVER_URL).createQueueConnection() }
                    }
                        .deserialize(JmsJsonDeserializer(targetClass = BatteryState::class))
                        .map { result ->
                            result.record.value
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