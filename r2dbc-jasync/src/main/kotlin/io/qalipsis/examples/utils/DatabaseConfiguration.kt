package io.qalipsis.examples.utils

sealed class DatabaseConfiguration(
    val databaseName : String,
    val userName : String,
    val password : String,
    val port : Int,
){

    val tableName : String = "battery_state"

    class PostgresDatabaseConfiguration : DatabaseConfiguration(
        databaseName = "postgres",
        userName = "postgres",
        password = "root",
        port = 15432
    )

    class MariaDBDatabaseConfiguration : DatabaseConfiguration(
        databaseName = "iot",
        userName = "root",
        password = "root",
        port = 13306
    )
}