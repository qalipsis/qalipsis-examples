package io.qalipsis.examples.utils

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

@JsonIgnoreProperties(ignoreUnknown = true)
data class BatteryState(

    @field:JsonProperty("deviceId")
    val deviceId: String = "",

    @field:JsonProperty("timestamp")
    val timestamp: Instant = Instant.now(),

    @field:JsonProperty("batteryLevel")
    val batteryLevel: Int = 0
) {

    fun primaryKey() = "$deviceId:$timestamp"

}

