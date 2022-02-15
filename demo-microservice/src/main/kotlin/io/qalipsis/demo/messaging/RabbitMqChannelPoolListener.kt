package io.qalipsis.demo.messaging

import com.rabbitmq.client.Channel
import io.micronaut.context.annotation.Requires
import io.micronaut.rabbitmq.connect.ChannelInitializer
import jakarta.inject.Singleton

/**
 * RabbitMQ channel pool listener initializer.
 *
 * Declares queue, binding and exchange in RabbitMQ.
 *
 * @author Alexander Sosnovsky
 */
@Singleton
@Requires(property = "messaging.rabbitmq.enabled", notEquals = "false")
internal class RabbitMqChannelPoolListener : ChannelInitializer() {

    /**
     * Initializes the channel.
     *
     * @param channel Channel
     */
    override fun initialize(channel: Channel) {
        channel.exchangeDeclare(queueName, "direct", true)

        val queue = channel.queueDeclare(queueName, true, false, true, emptyMap()).queue
        channel.queueBind(queue, queueName, queueName)
    }

    companion object {

        const val queueName = "http-request"
    }
}
