package io.qalipsis.demo.security

import io.github.oshai.kotlinlogging.KotlinLogging
import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpRequest
import io.micronaut.security.authentication.AuthenticationFailureReason
import io.micronaut.security.authentication.AuthenticationRequest
import io.micronaut.security.authentication.AuthenticationResponse
import io.micronaut.security.authentication.provider.HttpRequestAuthenticationProvider
import jakarta.inject.Singleton

@Singleton
class AuthenticationProviderUserPassword : HttpRequestAuthenticationProvider<HttpRequest<*>> {

    @Value("\${app.security.login}")
    lateinit var login: String

    @Value("\${app.security.password}")
    lateinit var password: String

    /**
     * Authenticates the single user of the configuration.
     */
    override fun authenticate(
        requestContext: HttpRequest<HttpRequest<*>>?,
        authRequest: AuthenticationRequest<String, String>
    ): AuthenticationResponse =
        if (authRequest.identity == login && authRequest.secret == password) {
            log.debug { "New login : ${authRequest.identity}" }
            AuthenticationResponse.success(authRequest.identity)
        } else {
            AuthenticationResponse.failure(AuthenticationFailureReason.CREDENTIALS_DO_NOT_MATCH)
        }

    companion object {

        @JvmStatic
        private val log = KotlinLogging.logger { }
    }

}
