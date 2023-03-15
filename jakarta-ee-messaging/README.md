# Jakarta scenario with Qalipsis

This example demos you how to save, poll and search data with the plugin for Jakarta. </br>
You will find the scenarios: 
1. _Produce and consume from queue_ :  Records are produced to the queue, then consumed periodically from the same queue and compared to the original saved record.
2. _Produce and consume from topic_ :  Records are produced to the topic, then consumed periodically from the same topic and compared to the original saved record.

## How to start the scenario
1. Start the container for Jakarta from the `docker-compose.yml` with the command `docker-compose up` next to this README file
2. Execute the Gradle task `runCampaignForProduceAndConsumeFromQueue` with the command `gradle :jakarta-ee-messaging:runCampaignForProduceAndConsumeFromQueue` to see the produce and consume scenario in action.
3. Execute the Gradle task `runCampaignForProduceAndConsumeFromTopic` with the command `gradle :jakarta-ee-messaging:runCampaignForProduceAndConsumeFromTopic` to see the produce and consume scenario in action.