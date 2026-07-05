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

* Send data to the HTTP endpoint.
* Then run in parallel
    * Join with the data from Kafka and verify the latency of the message to be pushed to Kafka.
    * Join with the data from Elasticsearch and verify the latency of the message to be saved into Elasticsearch.

Analytics data are configured to be concurrently pushed to Elasticsearch, InfluxDB, TimescaleDB - available in the
Docker compose file.

## Execution

Execute the gradle tasks `gradlew :distributed-system:runDistributedDemo` to automatically start the required services
via Docker compose and execute the demo.

You will find resulting reports in `distributed-system/build/reports/qalipsis` and JUnit results in
`distributed-system/build/test-results/qalipsis`.

Alternatively, you can start the Docker compose file yourself and execute
`gradlew :distributed-system:runQalipsisWithGui` to use the GUI from QALIPSIS, accessible at http://localhost:8400 or
the API at http://localhost8400/api.

## Links

* [QALIPSIS GUI](http://localhost8400)
* [QALIPSIS API](http://localhost8400/api)
* [Elasticsearch](http://localhost:9200)
* [Kibana](http://localhost:5601)
* [Redpanda Console](http://localhost:28080)
* [PgAdmin](http://localhost:25433)
* [InfluxDB](http://localhost:18086)
