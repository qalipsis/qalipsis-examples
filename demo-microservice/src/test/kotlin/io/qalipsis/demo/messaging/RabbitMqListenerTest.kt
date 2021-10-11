package io.qalipsis.demo.messaging

import io.mockk.coVerifyOrder
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import io.qalipsis.demo.services.ElasticsearchService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * @author Alexander Sosnovsky
 */
@ExtendWith(MockKExtension::class)
internal class RabbitMqListenerTest {

    @RelaxedMockK
    lateinit var elasticsearchService: ElasticsearchService

    @InjectMockKs
    lateinit var rabbitMqListener: RabbitMqListener

    @Test
    fun `check rabbitMq consumer forwards data to the elasticsearch service`() {
        val key1 = "the key"
        val data1 = """  { "q": "z" } """
        val data2 = """  { "q": "w" } """

        // when
        rabbitMqListener.receive(mapOf("messageKey" to key1), data1)
        rabbitMqListener.receive(emptyMap(), data2)

        // then
        coVerifyOrder {
            elasticsearchService.save(eq(listOf(key1 to data1)))
            elasticsearchService.save(eq(listOf(null to data2)))
        }
    }

}
