package io.qalipsis.example.tcpecho

import assertk.all
import assertk.assertThat
import assertk.assertions.isLessThan
import assertk.assertions.isNotNull
import assertk.assertions.prop
import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.executionprofile.more
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.map
import io.qalipsis.api.steps.returns
import io.qalipsis.api.steps.verify
import io.qalipsis.plugins.netty.RequestResult
import io.qalipsis.plugins.netty.netty
import io.qalipsis.plugins.netty.tcp.ConnectionAndRequestResult
import io.qalipsis.plugins.netty.tcp.spec.closeTcp
import io.qalipsis.plugins.netty.tcp.spec.tcp
import io.qalipsis.plugins.netty.tcp.spec.tcpWith
import java.time.Duration

/**
 *
 * Example to create a very simple scenario that sends a TCP request to an echo server and verifies the responses.
 *
 * Start the docker-compose script at the root of the module and the project to setup the test environment.
 *
 * @author Eric Jessé
 */
class TcpEchoScenario {

    val minions = 500

    @Scenario("tcp-echo")
    fun myScenario() {
        scenario {
            minionsCount = minions
            profile {
                more(1000, 10, 1.2, 200)
            }
        }
            .start()
            .returns<String> { context ->
                "Hello World! I'm the minion ${context.minionId}"
            }.configure {
                name = "generate-message"
            }
            .netty().tcp {
                name = "tcp-connect-and-query"
                iterate(100, Duration.ofMillis(500))
                connect {
                    address("localhost", 2701)
                    noDelay = true
                }
                request { _, input ->
                    input.toByteArray()
                }

                monitoring {
                    events = true
                    meters = true
                }

                report {
                    reportErrors = true
                }
            }
            .verify {
                assertThat(it).all {
                    prop(ConnectionAndRequestResult<String, ByteArray>::meters).all {
                        prop(ConnectionAndRequestResult.Meters::timeToSuccessfulConnect).isNotNull()
                            .isLessThan(Duration.ofMillis(1000))
                    }
                }
            }.configure {
                name = "verify-tcp-connect-and-query"
            }
            .map { it.response!! }
            .netty().tcpWith("tcp-connect-and-query") {
                name = "tcp-request"
                request { _, input -> input }
                iterate(50, Duration.ofSeconds(1))
            }
            .verify {
                assertThat(it).all {
                    prop(RequestResult<ByteArray, ByteArray, *>::meters).all {
                        prop(RequestResult.Meters::timeToFirstByte).isNotNull().isLessThan(Duration.ofMillis(300))
                        prop(RequestResult.Meters::timeToLastByte).isNotNull().isLessThan(Duration.ofMillis(500))
                    }
                }
            }.configure {
                name = "verify-tcp-request"
            }
            .netty().closeTcp("tcp-connect-and-query")
    }
}
