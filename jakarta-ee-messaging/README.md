# Jakarta scenario with Qalipsis

This example demos you how to save, poll and search data with the plugin for InfluxDB. </br>
You will find the scenarios: 
1. _Produce and consume from queue_ :  
2. _Produce and consume from topic_ :  

[//]: # (//TODO: add description)


## How to start the scenario
1. Start the container for Jakarta from the `docker-compose.yml` with the command `docker-compose up` next to this README file
2. Execute the Gradle task `runCampaignForProduceAndConsume` with the command `gradle :jakarta-ee-messaging:runCampaignForProduceAndConsume` to see the produce and consume scenario in action.