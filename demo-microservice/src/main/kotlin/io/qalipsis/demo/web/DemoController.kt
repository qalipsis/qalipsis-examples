package io.qalipsis.demo.web

import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.Post
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.demo.messaging.Publisher

/**
 * Demo controller.
 *
 * @author Alexander Sosnovsky
 */
@Secured(SecurityRule.IS_AUTHENTICATED)
@Controller("/")
class DemoController(val publishers: Collection<Publisher>) {

    init {
        log.info { "Registered publishers" }
    }

    /**
     * Receives the data from the request and sends to the publishers
     *
     * @param body Request body
     */
    @Post("/data")
    fun received(@Header("message-key") messageKey: String?, @Body body: String) {
        publishers.forEach {
            kotlin.runCatching {
                it.send(messageKey, body)
            }
        }
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }

}
