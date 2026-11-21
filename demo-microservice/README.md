# How to use the demo-microservice?

## Purpose

This microservice aims at being used as several instances in a distributed context, in order to simulate a basic system
for Qalipsis, for demo purpose.

It delivers the following features:

* HTTP login and creation of a session,
* HTTP sessions persisted into Redis,
* HTTP data ingestion and messages pushed to either Kafka, RabbitMQ or both,
* Messages consumed to either Kafka, RabbitMQ or both and saved into Elasticsearch.

The idea consists in deploying several instances of this service, while enabling only part of them on each instance. See
the configuration below to achieve that.

NOTE: The configuration for Redis and for the database (`DATASOURCES_DEFAULT_URL`, `DATASOURCES_DEFAULT_USERNAME`,
`DATASOURCES_DEFAULT_PASSWORD`) is always required: the connection pool opens when the service starts.

## Configuration

The service can be executed in different modes to serve different purposes. The HTTP server is always running, but the
other features are disabled by default. The server generates a self-signed SSL certificate, so disable the validation of
certificate on client side to be able to use it.

By default, the server uses HTTP/2. Both HTTP/2 cleartext and HTTP/2 are enabled, and HTTP/2 cleartext redirects to
HTTP/2.

### Configuring the HTTP Server

In order to sign in, an allowed user has to be configured into the service at startup.

Set the following environment variables to change the default:

* SECURITY_LOGIN=login-of-the-user - defaults to test
* SECURITY_PASSWORD=password-of-the-user - defaults to test

Configuration of the server:

* SERVER_HTTP_VERSION=1.1 - defaults to 2.0
* SERVER_DUAL_PROTOCOL=false - defaults to true, which enables the support of HTTP next to HTTPS
* SERVER_HTTP_TO_HTTPS_REDIRECT=false - defaults to true, which enables the redirection from HTTP to HTTPS
* NETTY_LOG_LEVEL=TRACE - defaults to ERROR to reduce the verbosity, should be uppercase

### Configuring Elasticsearch

Set the following environment variable:

* ELASTICSEARCH_HTTP_HOSTS=http://my-elasticsearch:9200 - defaults to http://localhost:9200

### Configuring Redis

Set the following environment variable:

* REDIS_URI=redis://my-redis:6379 - defaults to redis://localhost:6379

### Enable the Kafka producer

Set the following environment variables:

* KAFKA_ENABLED=true - defaults to false
* MESSAGING_KAFKA_LISTENER_ENABLED=true - defaults to false

You can configure the Kafka client further:

* KAFKA_BOOTSTRAP_SERVERS=my-kafka:9092 - defaults to localhost:9092
* KAFKA_LINGER_MS=500 - defaults to 0
* KAFKA_COMPRESSION_TYPE=gzip - defaults to lz4

### Enable the Kafka consumer

Set the following environment variables:

* KAFKA_ENABLED=true - defaults to false
* MESSAGING_KAFKA_LISTENER_ENABLED=true - defaults to false

You can configure the Kafka client further:

* KAFKA_BOOTSTRAP_SERVERS=my-kafka:9092 - defaults to localhost:9092

### Enable the RabbitMQ producer

Set the following environment variables:

* RABBITMQ_ENABLED=true - defaults to false
* MESSAGING_RABBITMQ_PUBLISHER_ENABLED=true - defaults to false

You can configure the Kafka client further:

* RABBITMQ_URI=amqp://my-rabbitmq:5672 - defaults to amqp://localhost:5672

### Enable the RabbitMQ consumer

Set the following environment variables:

* RABBITMQ_ENABLED=true - defaults to false
* MESSAGING_RABBITMQ_LISTENER_ENABLED=true - defaults to false

You can configure the Kafka client further:

* RABBITMQ_URI=amqp://my-rabbitmq:5672 - defaults to amqp://localhost:5672

## How to use the HTTP server to sign in and push data

### Sign in

Post a request to the endpoint /login with a JSON payload as follow:

```
{
    "username": "test",
    "password": "test"
}
```

The response contains a session cookie to be reused in further requests to push data.

### Push data

Post a request to the endpoint /data with any kind of JSON payload. Do not forget to add the session cookie to pass
through the security filter.

If you want to set a key on the message published to RabbitMQ or Kafka, you can set the HTTP header "message-key".

## Build

The service is a build of its own, apart from the QALIPSIS examples: Micronaut 5, Kotlin 2, Gradle 9.
Any Java from 17 up starts Gradle; Gradle itself and the service run on JDK 25, which Gradle downloads when
it is missing (`gradle/gradle-daemon-jvm.properties` and the Kotlin toolchain).

```shell
./gradlew build     # compile and test (the tests start PostgreSQL in Docker)
./gradlew docker    # the Docker image, a GraalVM native executable, for the architecture of this machine
```

`./gradlew docker` compiles the native executable inside the GraalVM builder image (`src/main/docker/Dockerfile`)
and loads the image into the local Docker, as `aerisconsulting/qalipsis-demo-microservice`, tagged with the version
of `build.gradle.kts` and `latest`. `DOCKER_PLATFORMS` selects other architectures, `DOCKER_BUILD_ARGS=--push`
publishes them.

The published image is for **linux/amd64 and linux/arm64**: the workflow *demo-microservice image*
(`.github/workflows/demo-microservice-image.yml`, run by hand) builds each architecture on a runner of its own and
merges them into one tag. The amd64 build cannot run emulated on Apple silicon: the GraalVM builder image needs an
x86-64-v3 CPU, which the emulator does not provide.

## Gradle tasks to run the server

Two Gradle tasks run the service against the services of `../distributed-system/docker-compose.yml`:

* runHttpToKafkaServer: listens on HTTP(S) and publishes the data to Kafka
* runKafkaToDbServer: consumes the data from Kafka and saves it into TimescaleDB