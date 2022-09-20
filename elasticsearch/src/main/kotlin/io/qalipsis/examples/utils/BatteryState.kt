package io.qalipsis.examples.utils

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import java.time.Instant

data class BatteryState @JsonCreator constructor(val deviceId: String, val timestamp: Instant, val batteryLevel: Int) {

    @get:JsonIgnore
    val primaryKey = "$deviceId:$timestamp"

}

