package io.qalipsis.demo.services

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import io.micronaut.transaction.SynchronousTransactionManager
import io.qalipsis.demo.entity.DeviceState
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.sql.Connection
import kotlin.math.pow

/**
 * J2DBC service integration tests.
 *
 * This class provides integration tests for J2DBC service.
 *
 * @author Alexander Sosnovsky
 */
@Testcontainers
@MicronautTest(startApplication = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class JdbcServiceIntegrationTest : TestPropertyProvider {

    @Inject
    private lateinit var jdbcService: JdbcService

    @Inject
    private lateinit var transactionManager: SynchronousTransactionManager<Connection>

    private val schema: String = "public"

    override fun getProperties(): MutableMap<String, String> {
        return mutableMapOf(
            "micronaut.session.http.redis.enabled" to "false",
            "messaging.rabbitmq.enabled" to "false",
            "messaging.kafka.enabled" to "false",
            "datasources.default.url" to pgsql.jdbcUrl,
            "datasources.default.username" to pgsql.username,
            "datasources.default.password" to pgsql.password,
            "liquibase.enabled" to "true"
        )
    }

    @AfterEach
    fun tearDown() {
        transactionManager.executeWrite {
            it.connection.createStatement().use {
                it.execute("TRUNCATE TABLE $schema.device_state")
            }
        }
    }

    @Test
    fun `should save the records`() {
        // when
        val keyForSave1 = """this-is-a-key"""
        val deviceState1 = DeviceState(
            deviceId = "device 1",
            timestamp = System.currentTimeMillis() - 1423,
            positionLat = 45.54,
            positionLon = 23.54,
            batteryLevelPercentage = 60
        )
        val deviceState2 = DeviceState(
            deviceId = "device 2",
            timestamp = System.currentTimeMillis(),
            positionLat = 12.653,
            positionLon = 65.98,
            batteryLevelPercentage = 30
        )
        jdbcService.save(
            listOf(
                keyForSave1 to deviceState1,
                null to deviceState2
            )
        )

        // then
        val states = transactionManager.executeRead {
            it.connection.prepareStatement("SELECT * FROM $schema.device_state ORDER BY device_id")
                .executeQuery()
        }
        assertThat(states).all {
            transform { it.next(); it }.all {
                transform { it.getString("device_id") }.isEqualTo("device 1")
                transform { it.getLong("timestamp") }.isEqualTo(deviceState1.timestamp)
                transform { it.getLong("saving_timestamp") }.isGreaterThan(0)
                transform { it.getDouble("position_lat") }.isEqualTo(45.54)
                transform { it.getDouble("position_lon") }.isEqualTo(23.54)
                transform { it.getInt("battery_level_percentage") }.isEqualTo(60)
                transform { it.getString("message_key") }.isEqualTo("this-is-a-key")
                transform { it.isFirst }.isTrue()
            }
            transform { it.next(); it }.all {
                transform { it.getString("device_id") }.isEqualTo("device 2")
                transform { it.getLong("timestamp") }.isEqualTo(deviceState2.timestamp)
                transform { it.getLong("saving_timestamp") }.isGreaterThan(0)
                transform { it.getDouble("position_lat") }.isEqualTo(12.653)
                transform { it.getDouble("position_lon") }.isEqualTo(65.98)
                transform { it.getInt("battery_level_percentage") }.isEqualTo(30)
                transform { it.getString("message_key") }.isNull()
                transform { it.isLast }.isTrue()
            }
        }
    }

    companion object {

        @Container
        @JvmStatic
        private val pgsql =
            PostgreSQLContainer<Nothing>(
                DockerImageName.parse("timescale/timescaledb:latest-pg14")
                    .asCompatibleSubstituteFor("postgres")
            ).apply {
                withCreateContainerCmdModifier { cmd ->
                    cmd.hostConfig!!.withMemory(512 * 1024.0.pow(2).toLong()).withCpuCount(2)
                }
                withInitScript("pgsql-init.sql")
            }
    }
}
