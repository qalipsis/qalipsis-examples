package io.qalipsis.examples.r2dbcjasync

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

@JsonIgnoreProperties(ignoreUnknown = true)
data class BatteryState(

    @field:JsonProperty("device_id")
    val deviceId: String = "",

    @field:JsonProperty("timestamp")
    val timestamp: Instant = Instant.ofEpochMilli(0),

    @field:JsonProperty("battery_level")
    val batteryLevel: Int = 0
)
