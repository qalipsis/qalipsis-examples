package io.qalipsis.examples.mqtt

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.kotest.assertions.asClue
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.ints.shouldBeExactly
import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.executionprofile.regular
import io.qalipsis.api.messaging.deserializer.MessageJsonDeserializer
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.*
import io.qalipsis.examples.utils.BatteryState
import io.qalipsis.examples.utils.ScenarioConfiguration
import io.qalipsis.examples.utils.ServerConfiguration
import io.qalipsis.plugins.jackson.csv.csvToObject
import io.qalipsis.plugins.jackson.jackson
import io.qalipsis.plugins.netty.mqtt.publisher.MqttPublishRecord
import io.qalipsis.plugins.netty.mqtt.publisher.spec.mqttPublish
import io.qalipsis.plugins.netty.mqtt.subscriber.spec.mqttSubscribe
import io.qalipsis.plugins.netty.netty

class MQTTProduceAndConsume {

    private val objectMapper = ObjectMapper().also {
        it.registerModule(JavaTimeModule())
    }


    @Scenario("mqtt-produce-and-consume")
    fun mqttProduceAndConsume() {

        scenario {
            minionsCount = ScenarioConfiguration.NUMBER_MINION
            profile {
                regular(periodMs = 1000, minionsCountProLaunch = minionsCount)
            }
        }.start()
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
            }.map { it.value } // we transform the output of the CSV reader entries to utils.BatteryState
            .netty().mqttPublish {
                connect {
                    host = ServerConfiguration.HOST
                    port = ServerConfiguration.PORT
                }

                clientName("qalipsis-mqtt-client")

                records { _, batteryState ->
                    listOf(
                        MqttPublishRecord(
                            value = objectMapper.writeValueAsString(batteryState),
                            topicName = ServerConfiguration.TOPIC_NAME
                        )
                    )
                }
            }.map { it.input }.innerJoin(using = { correlationRecord ->
                println("Using ${correlationRecord.value}")
                correlationRecord.value.primaryKey
            }, on = {
                it.netty().mqttSubscribe {
                        connect {
                            host = ServerConfiguration.HOST
                            port = ServerConfiguration.PORT
                        }
                        clientName("qalipsis-mqtt-client-subscriber")
                        topicFilter(ServerConfiguration.TOPIC_NAME)
                    }.deserialize(MessageJsonDeserializer(BatteryState::class)).map { result ->
                        println("on ${result.value}")
                        result.value
                    }.filterNotNull()
            }, having = { correlationRecord ->
                println("Having ${correlationRecord.value}")
                correlationRecord.value.primaryKey
            }).filterNotNull().verify { result ->
                result.asClue {
                    assertSoftly {
                        it.first.batteryLevel shouldBeExactly (it.second).batteryLevel
                    }
                }
            }
    }

}