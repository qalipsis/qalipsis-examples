package io.qalipsis.examples.utils

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

@JsonIgnoreProperties(ignoreUnknown = true)
data class BatteryState(

    @field:JsonProperty(BatteryStateContract.DEVICE_ID)
    val deviceId: String = "",

    @field:JsonProperty(BatteryStateContract.TIMESTAMP)
    val timestamp: Instant = Instant.ofEpochMilli(0),

    @field:JsonProperty(BatteryStateContract.BATTERY_LEVEL)
    val batteryLevel: Int = 0
) {
    fun primaryKey() = "$deviceId:$timestamp"
}

