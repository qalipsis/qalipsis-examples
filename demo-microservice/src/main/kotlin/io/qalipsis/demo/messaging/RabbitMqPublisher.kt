package io.qalipsis.demo.messaging

import io.micronaut.context.annotation.Requires
import io.micronaut.rabbitmq.annotation.Binding
import io.micronaut.rabbitmq.annotation.RabbitClient
import io.micronaut.rabbitmq.annotation.RabbitHeaders
import jakarta.inject.Singleton


/**
 * Implementation of [Publisher] to support data production to RabbitMQ.
 *
 * @author Alexander Sosnovsky
 */
@Singleton
@Requires(property = "messaging.rabbitmq.publisher.enabled", value = "true", defaultValue = "false")
internal class RabbitMqPublisher(
    private val client: RabbitMqClient
) : Publisher {

    override fun send(key: String?, message: String) {
        client.send(key?.takeIf(String::isBlank)?.let { mapOf("messageKey" to it) } ?: emptyMap(), message)
    }
}


/**
 * Implementation of [Publisher] to support data production to RabbitMQ.
 *
 * @author Alexander Sosnovsky
 */
@RabbitClient
interface RabbitMqClient {

    /**
     * Sends message to the exchange.
     *
     * @param message Message
     */
    @Binding(RabbitMqChannelPoolListener.queueName)
    fun send(@RabbitHeaders headers: Map<String, String>, message: String)
}
