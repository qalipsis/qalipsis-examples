package io.qalipsis.examples.jakarta

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
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
import io.qalipsis.plugins.jakarta.consumer.consume
import io.qalipsis.plugins.jakarta.deserializer.JakartaJsonDeserializer
import io.qalipsis.plugins.jakarta.destination.Queue
import io.qalipsis.plugins.jakarta.jakarta
import io.qalipsis.plugins.jakarta.producer.JakartaMessageType
import io.qalipsis.plugins.jakarta.producer.JakartaProducerRecord
import io.qalipsis.plugins.jakarta.producer.produce
import jakarta.jms.Session
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory

class JakartaProduceAndConsumeFromQueue {

    private val objectMapper = ObjectMapper().also {
        it.registerModule(JavaTimeModule())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Scenario("jakarta-produce-and-consume-from-queue")
    fun scenarioProduceAndConsumeFromQueue() {

        // we define the scenario, set the name, number of minions and rampUp
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
                    column(name = "deviceId")
                    column(name = "timestamp")
                    column(name = "batteryLevel").integer()
                }
                unicast()
            }
            .map { it.value } // we transform the output of the CSV reader entries to utils.BatteryState
            .jakarta()
            .produce {

                connect {
                    ActiveMQConnectionFactory(
                        "tcp://localhost:61616",
                        "qalipsis_user",
                        "qalipsis_password"
                    ).createQueueConnection()
                }

                session {
                    it.createSession(false, Session.AUTO_ACKNOWLEDGE)
                }

                records { _, input ->
                    listOf(
                        JakartaProducerRecord(
                            destination = Queue("battery_state"),
                            messageType = JakartaMessageType.BYTES,
                            value = objectMapper.writeValueAsBytes(input)
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
                it.jakarta().consume {
                    queues("battery_state")
                    queueConnection {
                        ActiveMQConnectionFactory(
                            "tcp://localhost:61616",
                            "qalipsis_user",
                            "qalipsis_password"
                        ).createQueueConnection()
                    }
                }
                    .deserialize(JakartaJsonDeserializer(targetClass = BatteryState::class))
                    .map { result ->
                        result.record.value
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