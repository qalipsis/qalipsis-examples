package io.qalipsis.demo.messaging

import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verifyOrder
import io.qalipsis.demo.entity.DeviceState
import io.qalipsis.demo.services.JdbcService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * @author Alexander Sosnovsky
 */
@ExtendWith(MockKExtension::class)
internal class RabbitMqListenerTest {

    @RelaxedMockK
    lateinit var jdbcService: JdbcService

    @InjectMockKs
    lateinit var rabbitMqListener: RabbitMqListener

    @Test
    fun `check rabbitMq consumer forwards data to the elasticsearch service`() {
        val key1 = "the key"
        val data1 = mockk<DeviceState>()
        val data2 = mockk<DeviceState>()

        // when
        rabbitMqListener.receive(mapOf("messageKey" to key1), data1)
        rabbitMqListener.receive(emptyMap(), data2)

        // then
        verifyOrder {
            jdbcService.save(eq(listOf(key1 to data1)))
            jdbcService.save(eq(listOf(null to data2)))
        }
    }

}
