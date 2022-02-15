package io.qalipsis.demo.messaging

/**
 * Publisher interface.
 *
 * Describes how to publish data to the destination channel.
 *
 * @author Alexander Sosnovsky
 */
internal interface Publisher {

    /**
     * Publishes the message.
     *
     * @param key key of the message for the target platform, when supported
     * @param message body of the message
     */
    fun send(key: String?, message: String)
}
