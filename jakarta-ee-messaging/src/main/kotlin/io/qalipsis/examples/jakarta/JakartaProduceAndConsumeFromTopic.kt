package io.qalipsis.examples.jakarta

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
import io.qalipsis.plugins.jakarta.consumer.consume
import io.qalipsis.plugins.jakarta.deserializer.JakartaJsonDeserializer
import io.qalipsis.plugins.jakarta.destination.Queue
import io.qalipsis.plugins.jakarta.destination.Topic
import io.qalipsis.plugins.jakarta.jakarta
import io.qalipsis.plugins.jakarta.producer.JakartaMessageType
import io.qalipsis.plugins.jakarta.producer.JakartaProducerRecord
import io.qalipsis.plugins.jakarta.producer.produce
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory

class JakartaProduceAndConsumeFromTopic {

    private val objectMapper = ObjectMapper().also {
        it.registerModule(JavaTimeModule())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Scenario("jakarta-produce-and-consume-from-topic")
    fun scenarioProduceAndConsumeFromTopic() {

        val factory = ActiveMQConnectionFactory(ServerConfiguration.SERVER_URL, ServerConfiguration.CONTAINER_USERNAME, ServerConfiguration.CONTAINER_PASSWORD)

        val connection = factory.createConnection()

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
            .jakarta()
            .produce {

                connect {
                    connection
                }

                session {
                    connection.createSession()
                }

                records { _, input ->
                    listOf(
                        JakartaProducerRecord(
                            destination = Topic(ServerConfiguration.TOPIC_NAME),
                            messageType = JakartaMessageType.BYTES,
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
                    println(" Using ${correlationRecord.value} ")
                    correlationRecord.value.primaryKey()
                },

                on = {
                    it.jakarta().consume {
                        topics(ServerConfiguration.TOPIC_NAME)
                        topicConnection {  factory.createTopicConnection() }
                    }
                        .deserialize(JakartaJsonDeserializer(targetClass = BatteryState::class))
                        .map { result ->
                            println("on ${result.record.value}")
                            result.record.value
                        }
                },

                having = { correlationRecord ->
                    println(" Having ${correlationRecord.value} ")
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