package io.qalipsis.demo.security

import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpRequest
import io.micronaut.security.authentication.AuthenticationException
import io.micronaut.security.authentication.AuthenticationFailed
import io.micronaut.security.authentication.AuthenticationProvider
import io.micronaut.security.authentication.AuthenticationRequest
import io.micronaut.security.authentication.AuthenticationResponse
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.core.Flowable
import jakarta.inject.Singleton
import mu.KotlinLogging
import org.reactivestreams.Publisher

/**
 * User authentication provider.
 *
 * This class provides user authentication by username and password for HTTP requests.
 *
 * @author Alexander Sosnovsky
 */
@Singleton
class AuthenticationProviderUserPassword : AuthenticationProvider {

    @Value("\${app.security.login}")
    lateinit var login: String

    @Value("\${app.security.password}")
    lateinit var password: String

    /**
     * Authenticates a user.
     *
     * @param httpRequest HTTP request
     * @param authenticationRequest Authentication request
     * @return Authentication response
     */
    override fun authenticate(
        httpRequest: HttpRequest<*>?,
        authenticationRequest: AuthenticationRequest<*, *>
    ): Publisher<AuthenticationResponse> {
        return Flowable.create({ emitter ->
            if (authenticationRequest.identity == login && authenticationRequest.secret == password) {
                emitter.onNext(AuthenticationResponse.success(authenticationRequest.identity as String))
                emitter.onComplete()
                log.debug { "New login : ${authenticationRequest.identity}" }
            } else {
                emitter.onError(AuthenticationException(AuthenticationFailed()))
            }
        }, BackpressureStrategy.ERROR)
    }

    companion object {

        @JvmStatic
        private val log = KotlinLogging.logger { }
    }

}
