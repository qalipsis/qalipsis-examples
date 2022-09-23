CREATE TABLE battery_state (
    "id" varchar(255) primary key,
    device_id varchar(255) not null,
    "timestamp" int not null,
    battery_level int not null
);