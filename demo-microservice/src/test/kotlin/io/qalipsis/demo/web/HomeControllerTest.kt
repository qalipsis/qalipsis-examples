package io.qalipsis.demo.web

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
internal class HomeControllerTest {

    @InjectMockKs
    lateinit var homeController: HomeController

    @Test
    fun `check home controller returns response`() {
        assertThat(homeController.received()).isEqualTo("Welcome on the QALIPSIS demo microservice")
    }
}
