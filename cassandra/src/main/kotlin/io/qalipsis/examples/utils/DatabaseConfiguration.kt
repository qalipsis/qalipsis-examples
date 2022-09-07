package io.qalipsis.examples.utils

class DatabaseConfiguration {
    companion object {
        const val NUMBER_MINION = 20
        const val KEYSPACE = "iot"
        val SERVERS = listOf("localhost:9042")
        const val DATACENTER_NAME = "datacenter1"
        const val TABLE_NAME = "batteryState"
    }
}