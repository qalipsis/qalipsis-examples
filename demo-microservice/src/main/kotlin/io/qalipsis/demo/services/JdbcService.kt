package io.qalipsis.demo.services

import io.micronaut.context.annotation.Property
import io.micronaut.transaction.SynchronousTransactionManager
import io.micronaut.validation.Validated
import io.qalipsis.demo.entity.DeviceState
import jakarta.annotation.PostConstruct
import jakarta.inject.Singleton
import mu.KotlinLogging
import org.postgresql.util.PSQLException
import java.sql.Connection
import javax.validation.Valid

@Singleton
@Validated
internal class JdbcService(
    private val transactionManager: SynchronousTransactionManager<Connection>,
    @Property(name = "datasources.default.schema") private val schema: String
) {

    @PostConstruct
    fun prepareHyperTable() {
        transactionManager.executeWrite {
            it.connection.createStatement().use {
                try {
                    it.execute("CREATE EXTENSION IF NOT EXISTS timescaledb CASCADE;")
                    it.execute("SELECT create_hypertable('$schema.device_state', 'timestamp', chunk_time_interval => 86400000)")
                } catch (e: PSQLException) {
                    log.warn { e.serverErrorMessage }
                }
            }
        }
    }

    /**
     * Saves the records into the JDBC table.
     *
     * @param records records to save as pairs: the first member is the ID of the message, the second is the source
     */
    fun save(@Valid records: List<Pair<String?, DeviceState>>) {
        if (records.isNotEmpty()) {
            transactionManager.executeWrite {
                it.connection.prepareStatement(
                    """ INSERT INTO $schema.device_state (saving_timestamp, device_id, timestamp, position_lat, position_lon, battery_level_percentage, message_key)
                    VALUES (?, ?, ?, ?, ?, ?, ?)"""
                ).use { statement ->
                    records.forEach { (key, deviceState) ->
                        statement.setLong(1, System.currentTimeMillis())
                        statement.setString(2, deviceState.deviceId)
                        statement.setLong(3, deviceState.timestamp)
                        statement.setDouble(4, deviceState.positionLat)
                        statement.setDouble(5, deviceState.positionLon)
                        statement.setInt(6, deviceState.batteryLevelPercentage)
                        statement.setString(7, key?.takeIf(String::isNotBlank))

                        statement.addBatch()
                    }

                    statement.executeLargeBatch()
                }
            }
        }
    }

    private companion object {

        val log = KotlinLogging.logger { }
    }
}