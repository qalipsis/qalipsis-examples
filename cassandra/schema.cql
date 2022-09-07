CREATE KEYSPACE IF NOT EXISTS iot WITH replication = {'class': 'SimpleStrategy', 'replication_factor' : 1};

USE iot;

CREATE TABLE IF NOT EXISTS batteryState
(
    company      text,
    "timestamp"  timestamp,
    deviceid     text,
    batterylevel int,
    primary key ((company), "timestamp", "deviceid")
);
