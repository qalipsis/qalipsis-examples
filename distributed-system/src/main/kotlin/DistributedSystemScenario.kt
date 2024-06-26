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

package io.qalipsis.example.distributedsystem

import assertk.all
import assertk.assertThat
import assertk.assertions.*
import com.fasterxml.jackson.databind.ObjectMapper
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus
import io.qalipsis.api.annotations.Property
import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.executionprofile.regular
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.*
import io.qalipsis.api.steps.datasource.DatasourceRecord
import io.qalipsis.plugins.kafka.consumer.KafkaConsumerResult
import io.qalipsis.plugins.kafka.consumer.consume
import io.qalipsis.plugins.kafka.kafka
import io.qalipsis.plugins.kafka.serdes.jsonSerde
import io.qalipsis.plugins.netty.RequestResult
import io.qalipsis.plugins.netty.http.response.HttpResponse
import io.qalipsis.plugins.netty.http.spec.HttpVersion
import io.qalipsis.plugins.netty.http.spec.http
import io.qalipsis.plugins.netty.netty
import io.qalipsis.plugins.r2dbc.jasync.dialect.Protocol
import io.qalipsis.plugins.r2dbc.jasync.poll.poll
import io.qalipsis.plugins.r2dbc.jasync.r2dbcJasync
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.common.serialization.Serdes
import java.math.BigDecimal
import java.time.Duration

/**
 * Complex scenario to validate different operations in a distributed system using a HTTP server, Kafka,
 * a service consuming from Kafka and injecting the data into Elasticsearch.
 *
 * Start the docker-compose script at the root of the module and the project to setup the test environment.
 *
 * @author Eric Jessé
 */
@Suppress("DuplicatedCode")
class DistributedSystemScenario(
    private val eventsLogger: EventsLogger,
    @Property(name = "jdbc.port") private val jdbcPort: Int,
    @Property(name = "jdbc.database") private val jdbcDatabase: String,
    @Property(name = "jdbc.username") private val jdbcUsername: String,
    @Property(name = "jdbc.password") private val jdbcPassword: String
) {

    @Scenario("distributed-system")
    fun myScenario(
        @Property(name = "http.server.url") serverUrl: String,
        @Property(name = "http.client.pool.size") poolSize: Int,
        @Property(name = "kafka.bootstrap") kafkaBootstrap: String,
        objectMapper: ObjectMapper
    ) {
        scenario {
            minionsCount = 100
            profile {
                //immediate()
                regular(periodMs = 500, minionsCountProLaunch = 100)
                //stages {
                //    stage(40.0, Duration.ofSeconds(15), Duration.ofSeconds(20))
                //    stage(60.0, Duration.ofSeconds(10), Duration.ofSeconds(30))
                //}
            }
        }
            .start()
            .returns { ctx ->
                DeviceState(
                    deviceId = ctx.minionId,
                    timestamp = System.currentTimeMillis(),
                    positionLat = randomLatitude(),
                    positionLon = randomLongitude(),
                    batteryLevelPercentage = (Math.random() * 100).toInt()
                )
            }
            .netty().http {
                name = "http-data-push"
                connect {
                    url(url = serverUrl)
                    connectTimeout = Duration.ofMillis(2000)
                    version = HttpVersion.HTTP_2_0
                    tls { disableCertificateVerification = true }
                }
                if (poolSize > 0) {
                    pool { size = poolSize }
                }
                monitoring { all() }
                request { ctx, deviceState ->
                    simple(HttpMethod.POST, "/data")
                        .body(
                            body = objectMapper.writeValueAsBytes(deviceState),
                            contentType = HttpHeaderValues.APPLICATION_JSON
                        )
                        .addHeader(name = "message-key", value = ctx.minionId)
                }
            }
            .verify { result ->
                assertThat(result).all {
                    prop(RequestResult<*, HttpResponse<String>, *>::response).isNotNull().all {
                        prop(HttpResponse<*>::status).isEqualTo(HttpResponseStatus.ACCEPTED)
                    }
                    prop(RequestResult<*, HttpResponse<String>, *>::meters).all {
                        prop(RequestResult.Meters::timeToLastByte).isNotNull()
                            .isLessThan(Duration.ofMillis(300))
                    }
                }
            }.configure {
                name = "Verify data push"
                report {
                    reportErrors = true
                }
            }
            .map { it.input } // Only keep the device state as input for the next steps.
            .split {
                // Verify the data in Kafka and Elasticsearch in parallel, in order to avoid that
                // an error occurring on one prevents the other verification from running.
                verifyKafka(kafkaBootstrap)
            }
            .verifyJdbc()
    }

    private fun randomLongitude() = Math.random() * 360 - 180

    private fun randomLatitude() = Math.random() * 180 - 90


    /**
     * Verifies the correctness of the data in Elasticsearch and the latency.
     */
    private fun StepSpecification<*, DeviceState, *>.verifyJdbc() {
        innerJoin()
            .using { deviceState -> deviceState.value.deviceId }
            .on {
                it.r2dbcJasync()
                    .poll {
                        name = "poll.in"
                        protocol(Protocol.POSTGRESQL)
                        connection {
                            port = jdbcPort
                            username = jdbcUsername
                            password = jdbcPassword
                            database = jdbcDatabase
                        }
                        query("""SELECT * FROM device_state order by "timestamp"""")
                        pollDelay(Duration.ofSeconds(1))
                        monitoring { all() }
                    }.flatten()
            }
            .having { correlationRecord -> correlationRecord.value.value["device_id"] as? String }
            .configure {
                name = "join-request-in-timescale"
                timeout(10_000) // We expect the DB record to be available in the next 10 seconds.
                report {
                    reportErrors = true
                }
            }
            .execute { context: StepContext<Pair<DeviceState, DatasourceRecord<Map<String, Any?>>>?, Pair<DeviceState, DatasourceRecord<Map<String, Any?>>>?> ->
                val input = context.receive()
                val (deviceState, dbRecord) = input!!
                eventsLogger.debug(
                    "time-to-timescale",
                    Duration.ofMillis((dbRecord.value["saving_timestamp"] as Long) - deviceState.timestamp),
                    tags = context.toEventTags()
                )
                context.send(input)
            }
            .verify {
                val (deviceState, dbRecord) = it!!
                // The device state received from Kafka should be similar to the one sent by HTTP.
                assertThat(actual = dbRecord.value).isNotNull().all {
                    key("battery_level_percentage").isEqualTo(deviceState.batteryLevelPercentage)
                    key("position_lat").isNotNull().isInstanceOf(BigDecimal::class).transform { it.toDouble() }
                        .isBetween(deviceState.positionLat - 10e-3, deviceState.positionLat + 10e-3)
                    key("position_lon").isNotNull().isInstanceOf(BigDecimal::class).transform { it.toDouble() }
                        .isBetween(deviceState.positionLon - 10e-3, deviceState.positionLon + 10e-3)
                    key("message_key").isEqualTo(deviceState.deviceId)
                }
                // The device state should be received by saved into Timescale in the next 10 seconds after its push to the HTTP server.
                assertThat(actual = (dbRecord.value["saving_timestamp"] as Long) - deviceState.timestamp).isLessThan(
                    10000
                )
            }.configure {
                name = "Verify Timescale data"
                report {
                    reportErrors = true
                }
            }
    }

    /**
     * Verifies the correctness of the data in Kafka and the latency.
     */
    private fun StepSpecification<*, DeviceState, *>.verifyKafka(
        kafkaBootstrap: String
    ) {
        innerJoin()
            .using { correlationRecord -> correlationRecord.value.deviceId }
            .on {
                it.kafka()
                    .consume {
                        name = "consume-http-requests"
                        bootstrap(kafkaBootstrap)
                        topics("http-request")
                        groupId(groupId = "distributed-system-scenario-demo")
                        properties(
                            "max.poll.records" to "2000",
                            "fetch.max.wait.ms" to "500",
                            "fetch.min.bytes" to "1048576",
                            "fetch.max.bytes" to "52428800",
                            "max.partition.fetch.bytes" to "52428800",
                            "max.poll.records" to "1000"
                        )
                        pollTimeout(pollTimeout = 1000)
                        offsetReset(offsetReset = OffsetResetStrategy.EARLIEST)
                        monitoring { all() }
                    }.flatten(Serdes.ByteArray().deserializer(), jsonSerde<DeviceState>().deserializer())
            }
            .having { correlationRecord -> correlationRecord.value.record.value!!.deviceId }
            .configure {
                name = "join-request-with-kafka"
                timeout(duration = 10_000) // We expect the Kafka record to be available in the next 20 seconds.
                report {
                    reportErrors = true
                }
            }
            .execute { context: StepContext<Pair<DeviceState, KafkaConsumerResult<ByteArray?, DeviceState?>>?, Pair<DeviceState, KafkaConsumerResult<*, DeviceState?>>?> ->
                val input = context.receive()
                val (deviceState, kafkaResult) = input!!
                eventsLogger.debug(
                    "time-to-kafka",
                    Duration.ofMillis(kafkaResult.record.receivedTimestamp - deviceState.timestamp),
                    tags = context.toEventTags()
                )
                context.send(input)
            }
            .verify {
                val (deviceState, kafkaResult) = it!!
                // The device state received from Kafka should be similar to the one sent by HTTP.
                assertThat(actual = kafkaResult.record.value).isNotNull().isDataClassEqualTo(deviceState)
                // The device state should be received by Kafka in the next 5 seconds after its push to the HTTP server.
                assertThat(actual = kafkaResult.record.receivedTimestamp - deviceState.timestamp).isLessThan(5000)
            }.configure {
                name = "Verify Kafka data"
                report {
                    reportErrors = true
                }
            }
    }

    /**
     * State of the device sent vio HTTP to the server.
     */
    data class DeviceState(
        val deviceId: String,
        val timestamp: Long = 0,
        val positionLat: Double,
        val positionLon: Double,
        val batteryLevelPercentage: Int
    )

}
