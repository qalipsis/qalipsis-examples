package io.qalipsis.demo.web

import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

/**
 * Home controller.
 *
 * @author Alexander Sosnovsky
 */
@Controller
class HomeController {

    /**
     * Endpoint for default auth redirection (can be removed after disable redirection).
     */
    @Get(produces = [MediaType.TEXT_PLAIN])
    fun received(): String {
        return "Welcome on the QALIPSIS demo microservice"
    }

}
