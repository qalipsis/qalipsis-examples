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

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.assertions.asClue
import io.kotest.matchers.equality.shouldBeEqualToComparingFields
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.qalipsis.api.annotations.Property
import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.executionprofile.stages
import io.qalipsis.api.meters.steps.timer
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.filterNotNull
import io.qalipsis.api.steps.innerJoin
import io.qalipsis.api.steps.map
import io.qalipsis.api.steps.returns
import io.qalipsis.api.steps.verify
import io.qalipsis.plugins.http.ConnectionStrategyType
import io.qalipsis.plugins.http.configuration.defaults
import io.qalipsis.plugins.http.http
import io.qalipsis.plugins.http.httpApache
import io.qalipsis.plugins.http.request.HttpMethod
import io.qalipsis.plugins.kafka.configuration.defaults
import io.qalipsis.plugins.kafka.consumer.consume
import io.qalipsis.plugins.kafka.kafka
import io.qalipsis.plugins.kafka.serdes.jsonSerde
import io.qalipsis.plugins.sql.configuration.defaults
import io.qalipsis.plugins.sql.dialect.Protocol
import io.qalipsis.plugins.sql.poll.poll
import io.qalipsis.plugins.sql.sql
import java.time.Duration
import org.apache.hc.core5.http.HttpVersion
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.common.serialization.Serdes

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
    @Property(name = "jdbc.port") private val jdbcPort: Int,
    @Property(name = "jdbc.database") private val jdbcDatabase: String,
    @Property(name = "jdbc.username") private val jdbcUsername: String,
    @Property(name = "jdbc.password") private val jdbcPassword: String,
) {

    @Scenario(
        "distributed-system",
        version = "1.0",
        description = "Scenario to validate the asynchronous exchanges in a distributed system"
    )
    fun myScenario(
        @Property(name = "http.server.url") serverUrl: String,
        @Property(name = "http.client.pool.size") poolSize: Int,
        @Property(name = "kafka.bootstrap") kafkaBootstrap: String,
        objectMapper: ObjectMapper,
    ) {
        scenario {
            minionsCount = 100
            profile {
                stages {
                    stage(40.0, 20_000, 30_000)
                    stage(60.0, 20_000, 30_000)
                }
                httpApache().defaults {
                    connect {
                        url(serverUrl)
                        version = HttpVersion.HTTP_2_0
                        tls { disableCertificateVerification = true }
                        connectionStrategy {
                            if (poolSize > 0) {
                                shared = true
                                strategyType = ConnectionStrategyType.POOL
                            } else {
                                shared = true
                                strategyType = ConnectionStrategyType.WARMUP
                            }
                        }
                    }
                }
                kafka().defaults {
                    bootstrap(kafkaBootstrap)
                }
                sql().defaults {
                    protocol(Protocol.POSTGRESQL)
                    connection {
                        port = jdbcPort
                        username = jdbcUsername
                        password = jdbcPassword
                        database = jdbcDatabase
                    }
                }
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
            .httpApache().http {
                name = "http-data-push"
                request { ctx, deviceState ->
                    simple(HttpMethod.POST, "/data")
                        .body(
                            body = objectMapper.writeValueAsBytes(deviceState),
                            contentType = "application/json"
                        )
                        .addHeader(name = "message-key", value = ctx.minionId)
                }
            }
            .timer("time-to-last-byte") { _, result ->
                result.meters.timeToLastByte!!
            }.shouldSatisfy {
                percentile(95.0).isLessThan(Duration.ofMillis(130))
            }
            .verify { result ->
                result.response?.code shouldBe 202
            }
            .configure {
                name = "Verify data push"
                report {
                    reportErrors = true
                }
            }
            .map { it.input } // Only keep the device state as input for the next steps.
            .split {
                // Verify the data in Kafka and Elasticsearch in parallel, in order to avoid that
                // an error occurring on one prevents the other verification from running.
                verifyKafka()
            }
            .verifyJdbc()
    }

    /**
     * Verifies the correctness of the data in Kafka and the latency.
     */
    private fun StepSpecification<*, DeviceState, *>.verifyKafka() {
        innerJoin()
            .using { deviceState ->
                val deviceId = deviceState.value.deviceId
                val deviceTimestamp = deviceState.value.timestamp
                "$deviceId:$deviceTimestamp"
            }
            .on {
                it.kafka()
                    .consume {
                        name = "consume-http-requests"
                        topics("http-request")
                        groupId(groupId = "distributed-system-scenario-demo")
                        pollTimeout(pollTimeout = 1000)
                        offsetReset(offsetReset = OffsetResetStrategy.EARLIEST)
                    }.flatten(Serdes.ByteArray().deserializer(), jsonSerde<DeviceState>().deserializer())
            }
            .having { correlationRecord ->
                val deviceId = correlationRecord.value.record.value!!.deviceId
                val deviceTimestamp = correlationRecord.value.record.value!!.timestamp
                "$deviceId:$deviceTimestamp"
            }
            .configure {
                name = "join-request-with-kafka"
                timeout(duration = 30_000)
                report {
                    reportErrors = true
                }
            }
            .filterNotNull()
            .timer("time-to-kafka") { _, (deviceState, kafkaResult) ->
                Duration.ofMillis(kafkaResult.record.receivedTimestamp - deviceState.timestamp)
            }
            .verify { (deviceState, kafkaResult) ->
                // The device state received from Kafka should be similar to the one sent by HTTP.
                kafkaResult.record.value.shouldNotBeNull() shouldBeEqualToComparingFields deviceState
            }
            .configure {
                name = "Verify Kafka data"
                report {
                    reportErrors = true
                }
            }
    }

    /**
     * Verifies the correctness of the data in Elasticsearch and the latency.
     */
    private fun StepSpecification<*, DeviceState, *>.verifyJdbc() {
        innerJoin()
            .using { deviceState ->
                val deviceId = deviceState.value.deviceId
                val deviceTimestamp = deviceState.value.timestamp
                "$deviceId:$deviceTimestamp"
            }
            .on {
                it.sql()
                    .poll {
                        name = "poll.in"
                        query(""" SELECT * FROM device_state order by "timestamp" """)
                        pollDelay(Duration.ofSeconds(1))
                    }.flatten()
            }
            .having { correlationRecord ->
                val deviceId = correlationRecord.value.value["device_id"]?.toString()
                val deviceTimestamp = correlationRecord.value.value["timestamp"] as Long
                "$deviceId:$deviceTimestamp"
            }
            .configure {
                name = "join-request-in-timescale"
                timeout(30_000)
                report {
                    reportErrors = true
                }
            }
            .filterNotNull()
            .timer("time-to-db") { _, (deviceState, dbRecord) ->
                Duration.ofMillis((dbRecord.value["saving_timestamp"] as Long) - deviceState.timestamp)
            }.shouldSatisfy {
                percentile(95.0).isLessThan(Duration.ofMillis(1000))
            }
            .configure { name = "time-to-db" }
            .verify { (deviceState, dbRecord) ->
                // The device state received from Kafka should be similar to the one sent by HTTP.
                dbRecord.value.shouldNotBeNull().asClue { dbValues ->
                    dbValues["battery_level_percentage"] shouldBe deviceState.batteryLevelPercentage
                    dbValues["message_key"] shouldBe deviceState.deviceId
                }
            }
            .configure {
                name = "Verify Timescale data"
                report {
                    reportErrors = true
                }
            }
    }

    private fun randomLongitude() = Math.random() * 360 - 180

    private fun randomLatitude() = Math.random() * 180 - 90

    /**
     * State of the device sent via HTTP to the server.
     */
    data class DeviceState(
        val deviceId: String,
        val timestamp: Long = 0,
        val positionLat: Double,
        val positionLon: Double,
        val batteryLevelPercentage: Int,
    )

}
