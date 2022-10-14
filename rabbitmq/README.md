# RabbitMQ scenario with Qalipsis

This example demos you how to produce and consume data with the plugin for RabbitMQ. </br>
The producer sends BatteryState data on an exchange and the consumer read it periodically from a queue then compared to the original send data.

## How to start the scenario
1. Start the container for RabbitMQ from the `docker-compose.yml` with the command `docker-compose up` next to this README file
2. Execute the Gradle task `runCampaignForProduceAndConsume` with the command `gradle :rabbitmq:runCampaignProduceAndConsume` to see the produce and consume scenario in action.