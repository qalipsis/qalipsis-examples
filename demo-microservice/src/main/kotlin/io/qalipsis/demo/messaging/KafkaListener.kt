package io.qalipsis.demo.messaging

import io.micronaut.configuration.kafka.annotation.KafkaListener
import io.micronaut.configuration.kafka.annotation.OffsetReset
import io.micronaut.configuration.kafka.annotation.OffsetStrategy
import io.micronaut.configuration.kafka.annotation.Topic
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.demo.services.ElasticsearchService
import kotlinx.coroutines.runBlocking
import org.apache.kafka.clients.consumer.ConsumerRecord

/**
 * Listener consuming from Apache Kafka to save in Elasticsearch.
 *
 * @author Alexander Sosnovsky
 */
@KafkaListener(
    offsetReset = OffsetReset.EARLIEST, batch = true, threads = 2, offsetStrategy = OffsetStrategy.ASYNC
)
@Requires(property = "messaging.kafka.listener.enabled", value = "true", defaultValue = "false")
class KafkaListener(private val elasticsearchService: ElasticsearchService) {

    init {
        log.info { "Starting the Kafka listener" }
    }

    @Topic("http-request")
    fun receive(
        requests: List<String>, // Used only to allow Micronaut find the right deserializer.
        records: List<ConsumerRecord<ByteArray?, String>>
    ) = runBlocking {
        log.debug { "Received Kafka records: '$requests'" }
        elasticsearchService.save(records.map { record ->
            record.key()?.let { String(it, Charsets.UTF_8) } to record.value()
        })
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }
}
