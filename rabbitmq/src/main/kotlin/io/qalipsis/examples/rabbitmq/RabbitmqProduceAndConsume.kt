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


package io.qalipsis.examples.rabbitmq

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.rabbitmq.client.Channel
import com.rabbitmq.client.ConnectionFactory
import io.kotest.assertions.asClue
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.ints.shouldBeExactly
import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.executionprofile.regular
import io.qalipsis.api.messaging.deserializer.MessageJsonDeserializer
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.filterNotNull
import io.qalipsis.api.steps.innerJoin
import io.qalipsis.api.steps.map
import io.qalipsis.api.steps.verify
import io.qalipsis.examples.utils.BatteryState
import io.qalipsis.examples.utils.ScenarioConfiguration.NUMBER_MINION
import io.qalipsis.examples.utils.ServerConfiguration
import io.qalipsis.plugins.jackson.csv.csvToObject
import io.qalipsis.plugins.jackson.jackson
import io.qalipsis.plugins.rabbitmq.consumer.consume
import io.qalipsis.plugins.rabbitmq.producer.RabbitMqProducerRecord
import io.qalipsis.plugins.rabbitmq.producer.produce
import io.qalipsis.plugins.rabbitmq.rabbitmq
import java.io.IOException

class RabbitmqProduceAndConsume {

    private val objectMapper = ObjectMapper().also {
        it.registerModule(JavaTimeModule())
    }

    private fun createExchangeAndQueue(
        channel: Channel, queueName: String, routingKey: String,
        type: String = "direct"
    ) {
        channel.exchangeDeclare(queueName, type, true)

        val queue = channel.queueDeclare(queueName, true, false, false, emptyMap()).queue
        channel.queueBind(queue, queueName, routingKey)
    }

    @Scenario("rabbitmq-produce-and-consume")
    fun rabbitmqProduceAndConsume() {

        // This code creates the exchange and the queue that will be used by the scenario prior to its execution.
        try {
            ConnectionFactory().apply {
                host = ServerConfiguration.HOST
                port = ServerConfiguration.PORT
                username = ServerConfiguration.USER_NAME
                password = ServerConfiguration.PASSWORD
                createExchangeAndQueue(
                    newConnection().createChannel(),
                    ServerConfiguration.EXCHANGE,
                    ServerConfiguration.EXCHANGE
                )
            }
        } catch (e: IOException) {
            // Ignore the exception, the exchange might already exist.
        }

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
            .rabbitmq()
            .produce {

                connection {
                    host = ServerConfiguration.HOST
                    port = ServerConfiguration.PORT
                    username = ServerConfiguration.USER_NAME
                    password = ServerConfiguration.PASSWORD

                }

                records { _, input ->
                    listOf(
                        RabbitMqProducerRecord(
                            exchange = ServerConfiguration.EXCHANGE,
                            routingKey = ServerConfiguration.EXCHANGE,
                            props = null,
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
                    correlationRecord.value.primaryKey
                },
                on = {
                    it.rabbitmq()
                        .consume {
                            name = "consume"
                            connection {
                                host = ServerConfiguration.HOST
                                port = ServerConfiguration.PORT
                                username = ServerConfiguration.USER_NAME
                                password = ServerConfiguration.PASSWORD
                            }

                            queue(ServerConfiguration.EXCHANGE)

                        }
                        .deserialize(MessageJsonDeserializer(BatteryState::class)) // use this method to transform your RabbitMQ consumer data to a specific object of your class
                        .map { result ->
                            result.value
                        }
                        .filterNotNull()
                },
                having = { correlationRecord ->
                    correlationRecord.value.primaryKey
                }
            )
            .filterNotNull()
            .verify { result ->
                result.asClue {
                    assertSoftly {
                        it.first.batteryLevel shouldBeExactly (it.second).batteryLevel
                    }
                }
            }

    }

}