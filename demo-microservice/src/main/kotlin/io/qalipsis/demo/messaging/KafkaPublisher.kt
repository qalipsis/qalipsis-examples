package io.qalipsis.demo.messaging

import io.micronaut.configuration.kafka.annotation.KafkaClient
import io.micronaut.configuration.kafka.annotation.KafkaKey
import io.micronaut.configuration.kafka.annotation.Topic
import io.micronaut.context.annotation.Requires

/**
 * Implementation of [Publisher] to support data production to Apache Kafka.
 *
 * @author Alexander Sosnovsky
 */
@KafkaClient(id = "product-client")
@Requires(property = "messaging.kafka.publisher.enabled", value = "true", defaultValue = "false")
internal interface KafkaPublisher : Publisher {

    /**
     * Publishes the message to Kafka.
     *
     * @param message Message body
     */
    @Topic("http-request")
    override fun send(@KafkaKey key: String?, message: String)

}
