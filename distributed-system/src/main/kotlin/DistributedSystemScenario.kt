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
import assertk.assertions.isBetween
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThan
import assertk.assertions.isNotNull
import assertk.assertions.key
import assertk.assertions.prop
import com.fasterxml.jackson.databind.ObjectMapper
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus
import io.qalipsis.api.annotations.Property
import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.executionprofile.immediate
import io.qalipsis.api.executionprofile.stages
import io.qalipsis.api.meters.steps.throughput
import io.qalipsis.api.meters.steps.timer
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.filterNotNull
import io.qalipsis.api.steps.innerJoin
import io.qalipsis.api.steps.map
import io.qalipsis.api.steps.returns
import io.qalipsis.api.steps.verify
import io.qalipsis.plugins.kafka.configuration.defaults
import io.qalipsis.plugins.kafka.consumer.consume
import io.qalipsis.plugins.kafka.kafka
import io.qalipsis.plugins.kafka.serdes.jsonSerde
import io.qalipsis.plugins.netty.RequestResult
import io.qalipsis.plugins.netty.configuration.defaults
import io.qalipsis.plugins.netty.http.response.HttpResponse
import io.qalipsis.plugins.netty.http.spec.HttpVersion
import io.qalipsis.plugins.netty.http.spec.http
import io.qalipsis.plugins.netty.netty
import io.qalipsis.plugins.sql.configuration.defaults
import io.qalipsis.plugins.sql.dialect.Protocol
import io.qalipsis.plugins.sql.poll.poll
import io.qalipsis.plugins.sql.sql
import java.math.BigDecimal
import java.time.Duration
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
                netty().defaults {
                    httpConnection {
                        url(url = serverUrl)
                        connectTimeout = Duration.ofMillis(2000)
                        version = HttpVersion.HTTP_2_0
                        tls { disableCertificateVerification = true }
                        if (poolSize > 0) {
                            pool { size = poolSize }
                        }
                    }
                    monitoring { all() }
                }
                kafka().defaults {
                    bootstrap(kafkaBootstrap)
                    monitoring { all() }
                }
                sql().defaults {
                    protocol(Protocol.POSTGRESQL)
                    connection {
                        port = jdbcPort
                        username = jdbcUsername
                        password = jdbcPassword
                        database = jdbcDatabase
                    }
                    monitoring { all() }
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
            .netty().http {
                name = "http-data-push"
                request { ctx, deviceState ->
                    simple(HttpMethod.POST, "/data")
                        .body(
                            body = objectMapper.writeValueAsBytes(deviceState),
                            contentType = HttpHeaderValues.APPLICATION_JSON
                        )
                        .addHeader(name = "message-key", value = ctx.minionId)
                }
            }
            .throughput("http-requests-per-seconds")
            .verify { result ->
                assertThat(result).all {
                    prop(RequestResult<*, HttpResponse<String>, *>::response).isNotNull().all {
                        prop(HttpResponse<*>::status).isEqualTo(HttpResponseStatus.ACCEPTED)
                    }
                    prop(RequestResult<*, HttpResponse<String>, *>::meters).all {
                        prop(RequestResult.Meters::timeToLastByte).isNotNull()
                            .isLessThan(Duration.ofSeconds(2))
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
                verifyKafka()
            }
            .verifyJdbc()
    }

    /**
     * Verifies the correctness of the data in Kafka and the latency.
     */
    private fun StepSpecification<*, DeviceState, *>.verifyKafka() {
        innerJoin()
            .using { correlationRecord -> correlationRecord.value.deviceId }
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
            .having { correlationRecord -> correlationRecord.value.record.value?.deviceId }
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
                assertThat(actual = kafkaResult.record.value).isNotNull().isDataClassEqualTo(deviceState)
            }.configure {
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
            .using { deviceState -> deviceState.value.deviceId }
            .on {
                it.sql()
                    .poll {
                        name = "poll.in"
                        query("""SELECT * FROM device_state order by "timestamp"""")
                        pollDelay(Duration.ofSeconds(1))
                    }.flatten()
            }
            .having { correlationRecord -> correlationRecord.value.value["device_id"] as? String }
            .configure {
                name = "join-request-in-timescale"
                timeout(30_000)
                report {
                    reportErrors = true
                }
            }
            .filterNotNull()
            .timer("time-to-timescale") { _, (deviceState, dbRecord) ->
                Duration.ofMillis((dbRecord.value["saving_timestamp"] as Long) - deviceState.timestamp)
            }
            .configure { name = "time-to-timescale" }
            .verify { (deviceState, dbRecord) ->
                // The device state received from Kafka should be similar to the one sent by HTTP.
                assertThat(actual = dbRecord.value).isNotNull().all {
                    key("battery_level_percentage").isEqualTo(deviceState.batteryLevelPercentage)
                    key("position_lat").isNotNull().isInstanceOf(BigDecimal::class).transform { it.toDouble() }
                        .isBetween(deviceState.positionLat - 10e-3, deviceState.positionLat + 10e-3)
                    key("position_lon").isNotNull().isInstanceOf(BigDecimal::class).transform { it.toDouble() }
                        .isBetween(deviceState.positionLon - 10e-3, deviceState.positionLon + 10e-3)
                    key("message_key").isEqualTo(deviceState.deviceId)
                }
            }.configure {
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
