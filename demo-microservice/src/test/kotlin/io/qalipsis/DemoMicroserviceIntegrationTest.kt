package io.qalipsis

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isNotNull
import io.lettuce.core.RedisClient
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.cookie.Cookie
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import io.mockk.verifyOrder
import io.qalipsis.demo.messaging.Publisher
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.shaded.org.bouncycastle.util.encoders.Base64
import java.time.Duration
import kotlin.math.pow

/**
 * Demo microservice integration tests.
 *
 * This class provides integration tests for demo microservice.
 *
 * @author Alexander Sosnovsky
 */
@ExtendWith(MockKExtension::class)
@Testcontainers
@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class DemoMicroserviceIntegrationTest : TestPropertyProvider {

    @Inject
    lateinit var context: ApplicationContext

    @Inject
    lateinit var redisClient: RedisClient

    @Inject
    @field:Client("/")
    lateinit var client: HttpClient

    @RelaxedMockK
    lateinit var publisher: Publisher

    @MockBean(Publisher::class)
    fun publisher(): Publisher = publisher

    override fun getProperties(): MutableMap<String, String> {
        return mutableMapOf(
            "app.security.login" to "test",
            "app.security.password" to "test",
            "messaging.rabbitmq.enabled" to "false",
            "messaging.kafka.enabled" to "false",
            "redis.uri" to "redis://${redisContainer.containerIpAddress}:${redisContainer.getMappedPort(6379)}"
        )
    }

    @Test
    fun `demo microservice flow test`() {
        // Sending login request and checking the response.
        val loginRequest = HttpRequest.POST<Any>("/login", """{ "username": "test", "password": "test" }""")
        val loginResponse = client.toBlocking().exchange<Any, Any>(loginRequest)
        assertEquals(HttpStatus.SEE_OTHER, loginResponse.status)
        assertThat(loginResponse.header("set-cookie")).isNotNull()
        val requestBody1 = """{ "key": "value" }"""
        val requestBody2 = """{ "key": "another value" }"""

        // Checking secure request with session cookie.
        val sessionCookie = loginResponse.header("set-cookie")!!.split(";")[0].split("=")
        assertThat(sessionCookie).hasSize(2)

        val postForMessagesRequestWithMessageKey = HttpRequest.POST<Any>("/data", requestBody1)
            .cookie(Cookie.of(sessionCookie[0], sessionCookie[1]))
            .header("message-key", "this is the key")
        val postForMessagesResponseWithMessageKey =
            client.toBlocking().exchange<Any, Any>(postForMessagesRequestWithMessageKey)
        assertEquals(HttpStatus.OK, postForMessagesResponseWithMessageKey.status)


        val postForMessagesRequestWithoutMessageKey = HttpRequest.POST<Any>("/data", requestBody2)
            .cookie(Cookie.of(sessionCookie[0], sessionCookie[1]))
        val postForMessagesResponseWithoutMessageKey =
            client.toBlocking().exchange<Any, Any>(postForMessagesRequestWithoutMessageKey)
        assertEquals(HttpStatus.OK, postForMessagesResponseWithoutMessageKey.status)

        // Verifying that both publisher were called with the JSON payload.
        verifyOrder {
            publisher.send(eq("this is the key"), eq(requestBody1))
            publisher.send(isNull(), eq(requestBody2))
        }
        val sessionId = String(Base64.decode(sessionCookie[1]))

        // Checking session exists in redis.
        val connection = redisClient.connect()
        val sessions = connection.sync().zrange("qalipsis-demo:sessions:active-sessions", 0, -1)
        assertThat(sessions).contains(sessionId)
    }

    companion object {

        @JvmStatic
        @Container
        private val redisContainer = GenericContainer<Nothing>("redis:6.0.8")
            .apply {
                withCreateContainerCmdModifier { cmd ->
                    cmd.hostConfig!!.withMemory(50 * 1024.0.pow(2).toLong()).withCpuCount(2)
                }
                withExposedPorts(6379)
                waitingFor(Wait.forListeningPort())
                withStartupTimeout(Duration.ofSeconds(60))
                withClasspathResourceMapping("redis-v6.conf", "/etc/redis.conf", BindMode.READ_ONLY)
                withCommand("redis-server /etc/redis.conf")
            }
    }
}
