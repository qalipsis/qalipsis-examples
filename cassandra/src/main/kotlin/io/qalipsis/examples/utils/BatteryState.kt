package io.qalipsis.examples.utils

import com.fasterxml.jackson.annotation.JsonCreator
import java.time.Instant

data class BatteryState @JsonCreator constructor(val deviceId: String, val timestamp: Instant, val batteryLevel: Int) {
    val primaryKey = "$deviceId:$timestamp"
}

