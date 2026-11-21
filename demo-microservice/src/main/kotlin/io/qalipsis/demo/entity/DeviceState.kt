package io.qalipsis.demo.entity

import io.micronaut.serde.annotation.Serdeable
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero

@Serdeable
data class DeviceState(
    val savingTimestamp: Long = Long.MIN_VALUE,

    @field:NotBlank
    val deviceId: String,

    @field:PositiveOrZero
    val timestamp: Long,

    val positionLat: Double,

    val positionLon: Double,

    @field:PositiveOrZero
    @field:Max(100)
    val batteryLevelPercentage: Int,

    var messageKey: String? = null
)
