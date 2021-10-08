package io.qalipsis.demo.messaging

import io.micronaut.context.annotation.Requires
import io.micronaut.rabbitmq.annotation.Queue
import io.micronaut.rabbitmq.annotation.RabbitHeaders
import io.micronaut.rabbitmq.annotation.RabbitListener
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.demo.services.ElasticsearchService
import kotlinx.coroutines.runBlocking

/**
 * Implementation of [Listener] to support data receiving from RabbitMQ.
 *
 * @author Alexander Sosnovsky
 */
@RabbitListener
@Requires(property = "messaging.rabbitmq.listener.enabled", value = "true", defaultValue = "false")
class RabbitMqListener(private val elasticsearchService: ElasticsearchService) {

    init {
        log.info { "Starting the RabbitMQ listener" }
    }

    /**
     * Receives the message from queue and sends to elasticsearch.
     *
     * @param request Message body
     */
    @Queue(RabbitMqChannelPoolListener.queueName)
    fun receive(@RabbitHeaders headers: Map<String, String?>, request: String) = runBlocking {
        log.debug { "Received Rabbit MQ message: '$request'" }
        elasticsearchService.save(listOf(headers["messageKey"] to request))
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }
}
