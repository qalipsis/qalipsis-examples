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
import io.netty.handler.codec.http.cookie.Cookie
import io.qalipsis.api.annotations.Property
import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.executionprofile.stages
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.*
import io.qalipsis.plugins.kafka.consumer.consume
import io.qalipsis.plugins.kafka.kafka
import io.qalipsis.plugins.kafka.serdes.jsonSerde
import io.qalipsis.plugins.netty.RequestResult
import io.qalipsis.plugins.netty.http.request.SimpleHttpRequest
import io.qalipsis.plugins.netty.http.response.HttpResponse
import io.qalipsis.plugins.netty.http.spec.HttpVersion
import io.qalipsis.plugins.netty.http.spec.http
import io.qalipsis.plugins.netty.http.spec.httpWith
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
    @Property(name = "jdbc.port") private val jdbcPort: Int,
    @Property(name = "jdbc.database") private val jdbcDatabase: String,
    @Property(name = "jdbc.username") private val jdbcUsername: String,
    @Property(name = "jdbc.password") private val jdbcPassword: String
) {

    @Scenario("distributed-system")
    fun myScenario(
        @Property(name = "users.count", orElse = "3000") usersCount: Int,
        @Property(name = "http.server.url") serverUrl: String,
        @Property(name = "http.client.pool.size") poolSize: Int,
        @Property(name = "kafka.bootstrap") kafkaBootstrap: String,
        objectMapper: ObjectMapper
    ) {
        scenario {
            minionsCount = usersCount
            profile {
                stages {
                    stage(
                        minionsCount = usersCount,
                        rampUpDurationMs = 10000,
                        totalDurationMs = 30000,
                        resolutionMs = 500
                    )
                }
            }
        }
            .start()

            .netty()
            .http {
                name = "login"
                connect {
                    url(serverUrl)
                    connectTimeout = Duration.ofMillis(2000)
                    version = HttpVersion.HTTP_1_1
                    tls { disableCertificateVerification = true }
                }
                if (poolSize > 0) {
                    pool { size = poolSize }
                }

                monitoring { all() }
                request { _, _ ->
                    SimpleHttpRequest(HttpMethod.POST, "/login")
                        .body("""{ "username": "test", "password": "test" }""", HttpHeaderValues.APPLICATION_JSON)
                }
            }
            .verify { result ->
                assertThat(result).all {
                    prop(RequestResult<*, HttpResponse<String>, *>::response).isNotNull().all {
                        prop(HttpResponse<*>::status).isEqualTo(HttpResponseStatus.SEE_OTHER)
                        prop(HttpResponse<*>::headers).key("location").isEqualTo("/")
                        prop(HttpResponse<*>::cookies).all {
                            hasSize(1)
                            key("aeris-http-to-kafka-session")
                        }
                    }
                    prop(RequestResult<*, HttpResponse<String>, *>::meters).all {
                        prop(RequestResult.Meters::timeToLastByte).isNotNull().isLessThan(Duration.ofMillis(100))
                    }
                }
            }.configure {
                name = "verify-login"
            }
            .map { result ->
                DeviceState(
                    positionLat = randomLatitude(), positionLon = randomLongitude(),
                    batteryLevelPercentage = (Math.random() * 100).toInt()
                ) to result.response!!.cookies["aeris-http-to-kafka-session"]!!
            }.configure { name = "__" }

            .netty()
            .httpWith("login") {
                name = "push-device-state"
                request { ctx, (deviceState, cookie) ->
                    deviceState.deviceId = ctx.minionId
                    deviceState.timestamp = System.currentTimeMillis()
                    SimpleHttpRequest(HttpMethod.POST, "/data")
                        .body(objectMapper.writeValueAsBytes(deviceState), HttpHeaderValues.APPLICATION_JSON)
                        .addHeader("message-key", ctx.minionId)
                        .addCookies(cookie)
                }
                monitoring { all() }
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
                name = "verify-data-push"
            }

            .map { it.input.first } // Only keep the device state as input for the next steps.
            .split {
                verifyKafka(kafkaBootstrap)
            }
            .verifyJdbc()
    }

    private fun randomLongitude() = Math.random() * 360 - 180

    private fun randomLatitude() = Math.random() * 180 - 90

    /**
     * Verifies the correctness of the data in Elasticsearch and the latency.
     */
    private fun StepSpecification<RequestResult<Pair<DeviceState, Cookie>, HttpResponse<String>, *>, DeviceState, *>.verifyJdbc() {
        innerJoin(
            using = { deviceState -> deviceState.value.deviceId },
            on = {
                it.r2dbcJasync()
                    .poll {
                        name = "poll-timescaledb"
                        protocol(Protocol.POSTGRESQL)
                        connection {
                            port = jdbcPort
                            username = jdbcUsername
                            password = jdbcPassword
                            database = jdbcDatabase
                        }
                        query("""SELECT * FROM device_state order by "timestamp"""")
                        pollDelay(Duration.ofSeconds(2))
                    }.flatten()
            },
            having = { correlationRecord -> correlationRecord.value.value["device_id"] }
        ).configure {
            name = "join-request-in-timescaledb"
            timeout(10_000)
        }

            .verify {
                val (deviceState, dbRecord) = it!!
                // The device state received from Kafka should be similar to the one sent by HTTP.
                assertThat(dbRecord.value).isNotNull().all {
                    key("battery_level_percentage").isEqualTo(deviceState.batteryLevelPercentage)
                    key("position_lat").isNotNull().isInstanceOf(BigDecimal::class).transform { it.toDouble() }
                        .isBetween(deviceState.positionLat - 10e-3, deviceState.positionLat + 10e-3)
                    key("position_lon").isNotNull().isInstanceOf(BigDecimal::class).transform { it.toDouble() }
                        .isBetween(deviceState.positionLon - 10e-3, deviceState.positionLon + 10e-3)
                    key("message_key").isEqualTo(deviceState.deviceId)
                }
                // The device state should be received by saved into Timescale in the next 10 seconds after its push to the HTTP server.
                assertThat((dbRecord.value["saving_timestamp"] as Long) - deviceState.timestamp).isLessThan(10000)
            }.configure {
                name = "verify-timescaledb-data"
            }
    }

    /**
     * Verifies the correctness of the data in Kafka and the latency.
     */
    private fun StepSpecification<RequestResult<Pair<DeviceState, Cookie>, HttpResponse<String>, *>, DeviceState, *>.verifyKafka(
        kafkaBootstrap: String
    ) {
        innerJoin(
            using = { correlationRecord -> correlationRecord.value.deviceId },
            on = {
                it.kafka()
                    .consume {
                        name = "consume-http-requests"
                        bootstrap(kafkaBootstrap)
                        topics("http-request")
                        groupId("distributed-system-scenario-demo")
                        properties(
                            "max.poll.records" to "2000",
                            "fetch.max.wait.ms" to "500",
                            "fetch.min.bytes" to "1048576",
                            "fetch.max.bytes" to "52428800",
                            "max.partition.fetch.bytes" to "52428800",
                            "max.poll.records" to "1000"
                        )
                        pollTimeout(1000)
                        offsetReset(OffsetResetStrategy.LATEST)
                    }.flatten(Serdes.ByteArray().deserializer(), jsonSerde<DeviceState>().deserializer())
            },
            having = { correlationRecord -> correlationRecord.value.record.value!!.deviceId }
        ).configure {
            name = "join-request-with-kafka"
            timeout(3_000)
        }
            .verify {
                val (deviceState, kafkaResult) = it!!
                // The device state received from Kafka should be similar to the one sent by HTTP.
                assertThat(kafkaResult.record.value).isNotNull().isDataClassEqualTo(deviceState)
                // The device state should be received by Kafka in the next 5 seconds after its push to the HTTP server.
                assertThat(kafkaResult.record.receivedTimestamp - deviceState.timestamp).isLessThan(5000)
            }.configure {
                name = "verify-kafka-data"
            }
    }

    /**
     * State of the device sent vio HTTP to the server.
     */
    data class DeviceState(
        val positionLat: Double,
        val positionLon: Double,
        val batteryLevelPercentage: Int
    ) {
        lateinit var deviceId: String
        var timestamp: Long = 0
    }

}
