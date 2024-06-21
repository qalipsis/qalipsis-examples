package io.qalipsis.examples.mqtt

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.kotest.assertions.asClue
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.ints.shouldBeExactly
import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.executionprofile.immediate
import io.qalipsis.api.messaging.deserializer.MessageJsonDeserializer
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.filterNotNull
import io.qalipsis.api.steps.innerJoin
import io.qalipsis.api.steps.map
import io.qalipsis.api.steps.verify
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
            minionsCount = 20
            profile {
                immediate()
            }
        }.start()
            .jackson() // we start the jackson step to fetch data from the csv file. we will use the csvToObject method to map csv entries to list of utils.BatteryState object
            .csvToObject(mappingClass = BatteryState::class) {

                classpath(path = "battery-levels.csv")
                // we define the header of the csv file
                header {
                    column(name = "deviceId")
                    column(name = "timestamp")
                    column(name = "batteryLevel").integer()
                }
                unicast()
            }.map { it.value } // we transform the output of the CSV reader entries to utils.BatteryState
            .netty().mqttPublish {
                connect {
                    host = "localhost"
                    port = 11883
                }

                clientName("qalipsis-mqtt-client")

                records { _, batteryState ->
                    listOf(
                        MqttPublishRecord(
                            value = objectMapper.writeValueAsString(batteryState),
                            topicName = "battery-state"
                        )
                    )
                }
            }.map { it.input }.innerJoin()
            .using { correlationRecord ->
                correlationRecord.value.deviceId
            }
            .on {
                it.netty().mqttSubscribe {
                    connect {
                        host = "localhost"
                        port = 11883
                    }
                    clientName("qalipsis-mqtt-client-subscriber")
                    topicFilter("battery-state")
                }.deserialize(MessageJsonDeserializer(BatteryState::class)).map { result ->
                    result.value
                }.filterNotNull()
            }
            .having { correlationRecord ->
                correlationRecord.value.deviceId
            }
            .filterNotNull().verify { result ->
                result.asClue {
                    assertSoftly {
                        it.first.batteryLevel shouldBeExactly (it.second).batteryLevel
                    }
                }
            }
    }

}