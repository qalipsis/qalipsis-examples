# Redis scenario with Qalipsis

This example demos you how to save, poll, produce and consume data with the Redis plugin. <br/>
You will find two scenarios:

1. _Save and poll_ : Records are saved into the database with a key using the save step, then retrieved periodically from the same
   key and compared to the original saved record.
2. _Produce and Consume_ : The producer sends BatteryState with a specific key and the consumer read it periodically
   from the same key then compared to the original sent data.

## How to start the scenarios

1. Start the container for Redis from the `docker-compose.yml` with the command `docker-compose up` next to this
   README file
2. Execute the Gradle task `runCampaignForSaveAndPoll` with the command `gradle :redis:runCampaignForSaveAndPoll` to see
   the save and poll scenario in action.
3. Execute the Gradle task `runCampaignProduceAndConsume` with the command `gradle :redis:runCampaignProduceAndConsume`
   to see the produce and consume scenario in action.