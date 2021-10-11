package io.qalipsis.demo.messaging

import io.mockk.coVerify
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import io.qalipsis.demo.services.ElasticsearchService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * @author Alexander Sosnovsky
 */
@ExtendWith(MockKExtension::class)
internal class KafkaListenerTest {

    @RelaxedMockK
    lateinit var elasticsearchService: ElasticsearchService

    @InjectMockKs
    lateinit var kafkaListener: KafkaListener

    @Test
    fun `check kafka consumer forwards data to the elasticsearch service`() {
        val key1 = "the key"
        val data1 = """  { "q": "z" } """
        val data2 = """  { "q": "w" } """

        // when
        kafkaListener.receive(
            emptyList(),
            listOf(
                ConsumerRecord("", 0, 0, key1.toByteArray(), data1),
                ConsumerRecord("", 0, 2, null, data2)
            )
        )

        // then
        coVerify(exactly = 1) {
            elasticsearchService.save(eq(listOf(key1 to data1, null to data2)))
        }
    }

}
