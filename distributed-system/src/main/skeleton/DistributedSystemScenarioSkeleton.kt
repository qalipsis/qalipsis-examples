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
import io.qalipsis.api.executionprofile.regular
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
class DistributedSystemScenarioSkeleton {

    @Scenario("distributed-system")
    fun myScenario() {
        scenario {
            minionsCount = ...
            profile {
                stages {
                    stage(
                        ...
                    )
                }
            }
        }
            .start()

            .netty()
            .http {
                name = "login"
                connect {
                    url(...)
                }
                request { _, _ ->
                    SimpleHttpRequest(HttpMethod.POST, "/login")
                        .body("""{ "username": "test", "password": "test" }""", HttpHeaderValues.APPLICATION_JSON)
                }
            }
            .verify { result ->
                ...
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
                    SimpleHttpRequest(HttpMethod.POST, "/data")
                        .body("...", HttpHeaderValues.APPLICATION_JSON)
                        .addHeader("message-key", ctx.minionId)
                        .addCookies(cookie)
                }
            }
            .verify { result ->
                ...
            }.configure {
                name = "verify-data-push"
            }

            .map { it.input.first } // Only keep the device state as input for the next steps.

            .split {

                innerJoin(
                    using = { correlationRecord -> correlationRecord.value.deviceId },
                    on = {
                        it.kafka()
                            .consume {
                                name = "consume-http-requests"
                                bootstrap(...)
                                topics("http-request")
                                properties(
                                    ...
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
                        ...
                    }.configure {
                        name = "verify-kafka-data"
                    }

            }
            .innerJoin(
                using = { deviceState -> deviceState.value.deviceId },
                on = {
                    it.r2dbcJasync()
                        .poll {
                            name = "poll-timescaledb"
                            protocol(Protocol.POSTGRESQL)
                            connection {

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
                ...
            }.configure {
                name = "verify-timescaledb-data"
            }
    }

    private fun randomLongitude() = Math.random() * 360 - 180

    private fun randomLatitude() = Math.random() * 180 - 90


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
