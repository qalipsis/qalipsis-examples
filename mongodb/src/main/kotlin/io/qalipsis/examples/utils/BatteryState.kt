package io.qalipsis.examples.utils

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.Instant

@JsonIgnoreProperties(ignoreUnknown = true)
data class BatteryState(
    val deviceId: String = "",
    val timestamp: Instant = Instant.now(),
    val batteryLevel: Int = 0
) {
    val primaryKey = "$deviceId:$timestamp"
}

