# Scenario to test a distributed architecture

## Purpose

This scenario introduces a strategy to test different aspects of a distributed system.

The distributed system can be started with the file `docker-compose.yml`, and contains two microservices and different
tools.

The first microservice is a HTTP server, that published allowed requests to Kafka. This service requires a login and
keeps the session into Redis. The second microservice consumes the messages pushed from Kafka and saves them into
Elasticsearch.

See the [demo microservice documentation](../demo-microservice/README.md) for more details.

## What does the scenario

The scenario performs the following operations:

* Try to push data without login and verify that it is not allowed
* Log in and verify it succeeded
* Send data to the HTTP endpoint with the session cookie, 50 times
* Then run in parallel
    * Join with the data from Kafka and verify the latency of the message to be pushed to Kafka.
    * Join with the data from Elasticsearch and verify the latency of the message to be saved into Elasticsearch.

## Preparation

Start the docker environment with the file `docker-compose.yml`.

Execute the Gradle tasks to start two instances of the service, using
Kafka  [demo microservice documentation](../demo-microservice/README.md#gradle-tasks-to-run-the-server) for more
details.

Once Elasticsearch and Kibana are ready, go to the [dev console](http://localhost:5601/app/dev_tools#/console) and
create the index to receive the test data:

```
PUT http-requests
{
  "settings": {
    "refresh_interval": "1s"
  },
  "mappings": {
    "_source": {
      "enabled": true
    },
    "properties": {
      "@messageKey": {
        "type": "keyword"
      },
      "@savingTimestamp": {
        "type": "date",
        "format": "epoch_millis"
      },
      "batteryLevelPercentage": {
        "type": "integer"
      },
      "deviceId": {
        "type": "keyword"
      },
      "position": {
        "type": "geo_point"
      },
      "timestamp": {
        "type": "date",
        "format": "epoch_millis"
      }
    }
  }
}
```

Then start the demo scenario, using your configuration of choice for the size of the HTTP client pool, the number of
minions and so on...
