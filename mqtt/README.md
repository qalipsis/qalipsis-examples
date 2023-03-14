# MQTT scenario with Qalipsis

This example demos you how to produce and consume data with the plugin for netty. </br>
The producer sends BatteryState data on a topic and the consumer read it periodically then compared to the original send data.

## How to start the scenario
1. Start the container for MQTT from the `docker-compose.yml` with the command `docker-compose up` next to this README file
2. Execute the Gradle task `runCampaignProduceAndConsume` with the command `gradle :mqtt:runCampaignProduceAndConsume` to see the produce and consume scenario in action.