package io.qalipsis.demo.messaging

import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import io.qalipsis.demo.entity.DeviceState
import io.qalipsis.demo.services.JdbcService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * @author Alexander Sosnovsky
 */
@ExtendWith(MockKExtension::class)
internal class KafkaListenerTest {

    @RelaxedMockK
    lateinit var jdbcService: JdbcService

    @InjectMockKs
    lateinit var kafkaListener: KafkaListener

    @Test
    fun `check kafka consumer forwards data to the elasticsearch service`() {
        val key1 = "the key"
        val data1 = mockk<DeviceState>()
        val data2 = mockk<DeviceState>()

        // when
        kafkaListener.receive(
            emptyList(),
            listOf(
                ConsumerRecord("", 0, 0, key1.toByteArray(), data1),
                ConsumerRecord("", 0, 2, null, data2)
            )
        )

        // then
        verify(exactly = 1) {
            jdbcService.save(eq(listOf(key1 to data1, null to data2)))
        }
    }

}
