package io.qalipsis.demo.entity

import javax.validation.constraints.Max
import javax.validation.constraints.NotBlank
import javax.validation.constraints.PositiveOrZero

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
