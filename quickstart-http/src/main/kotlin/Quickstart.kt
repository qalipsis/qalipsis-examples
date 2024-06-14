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

package io.qalipsis.example.quickstart

import io.kotest.assertions.asClue
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContainOnlyOnce
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus
import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.executionprofile.stages
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.verify
import io.qalipsis.plugins.netty.http.request.SimpleHttpRequest
import io.qalipsis.plugins.netty.http.spec.HttpVersion
import io.qalipsis.plugins.netty.http.spec.http
import io.qalipsis.plugins.netty.http.spec.httpWith
import io.qalipsis.plugins.netty.netty
import java.time.Duration

@Suppress("DuplicatedCode")
class Quickstart {

    @Scenario("quickstart-http")
    fun quickstart() {
        scenario {
            minionsCount = 1_000
            profile {
                stages {
                    stage(100.0, 25_000, 30_000)
                }
            }
        }
            .start()
            .netty()
            .http {
                name = "quickstart-http-post"
                report {
                    reportErrors = true
                }
                connect {
                    url(url = "https://localhost:18443")
                    connectTimeout = Duration.ofMillis(1000)
                    version = HttpVersion.HTTP_2_0
                    tls { disableCertificateVerification = true }
                }
                request { _, _ ->
                    simple(HttpMethod.POST, "/echo").body(
                        "Hello World!",
                        HttpHeaderValues.TEXT_PLAIN
                    )
                }
            }
            .verify { result ->
                assertSoftly {
                    result.asClue {
                        it.response shouldNotBe null
                        it.response!!.asClue { response ->
                            response.status shouldBe HttpResponseStatus.OK
                            response.body shouldContainOnlyOnce "Hello World!"
                            response.body shouldContainOnlyOnce "POST"
                        }
                        it.meters.asClue { meters ->
                            meters.timeToFirstByte!! shouldBeLessThanOrEqualTo Duration.ofSeconds(1)
                            meters.timeToLastByte!! shouldBeLessThanOrEqualTo Duration.ofSeconds(2)
                        }
                    }
                }
            }.configure {
                name = "verify-post"
            }
            .netty()
            .httpWith("quickstart-http-post") {
                report {
                    reportErrors = true
                }
                request { _, _ ->
                    SimpleHttpRequest(HttpMethod.PATCH, "/echo").body(
                        "Hello World!",
                        HttpHeaderValues.TEXT_PLAIN
                    )
                }
            }.verify { result ->
                assertSoftly {
                    result.asClue {
                        it.response shouldNotBe null
                        it.response!!.asClue { response ->
                            response.status shouldBe HttpResponseStatus.OK
                            response.body shouldContainOnlyOnce "Hello World!"
                            response.body shouldContainOnlyOnce "PATCH"
                        }
                        it.meters.asClue { meters ->
                            meters.timeToFirstByte!! shouldBeLessThanOrEqualTo Duration.ofMillis(500)
                            meters.timeToLastByte!! shouldBeLessThanOrEqualTo Duration.ofMillis(1000)
                        }
                    }
                }
            }.configure {
                name = "verify-patch"
            }
    }

}
