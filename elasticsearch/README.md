# Elasticsearch scenario with Qalipsis

This example demos you how to save, poll and search data with the plugin for Elasticsearch. </br>
You will find two scenarios:
1. _Save and poll_ :  Records are saved into the database using the save step, then retrieved periodically from the same location and compared to the original saved record.
2. _Save and search_ : Records are saved into the database using the save step, then searched with a query from the same location and compared to the original saved record.

## How to start the scenarios
1. Start the container for Elasticsearch from the `docker-compose.yml` with the command `docker-compose up` next to this README file
2. Execute the Gradle task `runCampaignForSaveAndPoll` with the command `gradle :elasticsearch:runCampaignForSaveAndPoll` to see the save and poll scenario in action. _FYI: It's better to clear the database before launch this scenario._
3. Execute the Gradle task `runCampaignForSaveAndSearch` with the command `gradle :elasticsearch:runCampaignForSaveAndSearch` to see the save and search scenario in action.