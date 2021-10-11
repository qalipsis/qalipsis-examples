package io.qalipsis.demo.web

import io.mockk.coVerifyAll
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import io.qalipsis.demo.messaging.Publisher
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
internal class DemoControllerTest {

    @InjectMockKs
    lateinit var demoController: DemoController

    @RelaxedMockK
    lateinit var publishers: Collection<Publisher>

    @Test
    fun `check demo controller sends body to all publishers`() {
        val key = "the key"
        val body = """  { "q": "w" } """

        // when
        demoController.received(key, body)

        // then
        coVerifyAll {
            publishers.forEach { publisher ->
                publisher.send(eq(key), eq(body))
            }
        }

    }
}
