# Testing a HTTP server

## Purpose

In this quickstart, you will learn how to test a very simple echo-like HTTP service.

The HTTP service to use as a tested target can be started using the attached file `docker-compose.yml` and by running the
command `docker compose up -d` when located in its parent directory.

## What this quickstart will cover

In this quickstart, you will discover how to:

1. Execute plain HTTP queries with QALIPSIS as a standalone application,
2. Assert the response from the server,
3. Reuse a HTTP connection for further calls,
4. Create a Java archive to share your scenario,
5. Visualize the events in a log file.

## Create your project

In your browser, open the website [https://bootstrap.qalipsis.io](https://bootstrap.qalipsis.io).

1. Verify the prerequisites listed on the page and download the project skeleton.
2. Give the name "Quickstart" to your project.
3. In the QALIPSIS plugins section, add `netty()`.
4. In the dependencies, add `implementation("io.kotest:kotest-assertions-core:5.4.2")`.
5. Reload the Gradle project if you are working in an IDE or text editor that integrates Gradle.

## Start the HTTP server

Create a file `docker-compose.yml` with the following content:

```
services:
  http-punching-ball:
    image: aerisconsulting/http-punching-ball
    command:
      - "--https=true"
      - "--ssl-key=http-server.key"
      - "--ssl-cert=http-server.crt"
    ports:
      - "18080:8080"
      - "18443:8443"

```
Start the docker environment with the file `docker-compose.yml` with the command `docker compose up -d`.

## Prepare your scenario

### Open the scenario skeleton

Open the project archive in your IDE and edit the file `src/main/kotlin/my/bootstrap/MyBootstrapScenario.kt'
You should be able to see the following skeleton:

```kotlin
package my.bootstrap

import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.scenario.scenario

/**
 * A skeleton of scenario.
 */
class MyBootstrapScenario {

    @Scenario(name = "my-new-scenario", description = "It does something extraordinary", version = "0.1")
    fun myBootstrapScenario() {
        scenario {
            minionsCount = 100
            profile {

            }
        }
            .start()
        // Develop your scenario here.
    }
}

```

You can rename, as well as the wrapping folders under `src/main/kotlin`.
Note that they have to match the package name at the beginning of the file.

### Configure the scenario basics

Configure the scenario to execute with 10,000 minions, 100 of them starting every 500 ms.

```kotlin
        scenario("quickstart-http") {
            minionsCount = 10_000
            rampUp {
                immediate()
            }
        }
        .start()
```

### Send a HTTP request

Create a HTTP step to send a POST request to the server. After the `start()` statement that specifies the starting point
of load injection, access to the `netty` namespace and call the `http` operator:

1. Give your step a name.
2. Configure the connection to the server.
3. Configure the HTTP request to send: HTTP method, path and body.
4. Enable the reporting of errors for that step.

```kotlin
            .start()
            .netty()
            .http {
                name = "quickstart-http-post"
                connect {
                    url("http://localhost:18080")
                    version = HttpVersion.HTTP_1_1
                }
                request { _, _ ->
                    SimpleHttpRequest(HttpMethod.POST, "/echo").body(
                        "Hello World!",
                        HttpHeaderValues.TEXT_PLAIN
                    )
                }
                report {
                    reportErrors = true
                }
            }
```

At that point, you can execute your scenario using Gradle: `./gradlew run`.
You can observe the requests logged by the HTTP server. But is that enough to verify it is working?

### Assert the response: content and performance

In order to verify that the server is returning what we expect, let's add a verification step to the scenario, just
after the http step.

We are verifying that:

* the HTTP status of the response is OK (we are using `io.netty.handler.codec.http.HttpResponseStatus`)
* the body contains the string we sent in the request

```kotlin
            .start()
            .netty()
            .http {
               // ...
            }
            .verify { result ->
                assertSoftly {
                    result.asClue {
                        it.response shouldNotBe null
                        it.response!!.asClue { response ->
                            response.status shouldBe HttpResponseStatus.OK
                            response.body shouldContainOnlyOnce "Hello World!"
                            response.body shouldContainOnlyOnce "POST"
                        }
                        it.meters.asClue { meters ->
                            meters.timeToFirstByte!! shouldBeLessThanOrEqualTo Duration.ofSeconds(1)
                            meters.timeToLastByte!! shouldBeLessThanOrEqualTo Duration.ofSeconds(2)
                        }
                    }
                }
            }.configure {
                name = "verify-post"
            }
```

### Reuse a HTTP connection

In order to strength the relevance of your test, we are not only creating one HTTP connection by minion,
but also reusing it for the same minion in further steps.

To do so, we simply add a new Netty HTTP step after the validation, called `httpWith`, where we specify the name of the
step that holds the connection: in our case `quickstart-http-post`, which is the first HTTP step of our scenario.

You can not that there is logically no need to specify anything about the connection, since the one from `quickstart-http-post`
was kept active and is automatically closed after that new step.

For convenience purpose, we also add a new verification step.

```kotlin

            .netty()
            .httpWith("quickstart-http-post") {
                report {
                    reportErrors = true
                }
                request { _, _ ->
                    SimpleHttpRequest(HttpMethod.PATCH, "/echo").body(
                        "Hello World!",
                        HttpHeaderValues.TEXT_PLAIN
                    )
                }
            }.verify { result ->
                assertSoftly {
                    result.asClue {
                        it.response shouldNotBe null
                        it.response!!.asClue { response ->
                            response.status shouldBe HttpResponseStatus.OK
                            response.body shouldContainOnlyOnce "Hello World!"
                            response.body shouldContainOnlyOnce "PATCH"
                        }
                        it.meters.asClue { meters ->
                            meters.timeToFirstByte!! shouldBeLessThanOrEqualTo Duration.ofMillis(100)
                            meters.timeToLastByte!! shouldBeLessThanOrEqualTo Duration.ofMillis(200)
                        }
                    }
                }
            }.configure {
                name = "verify-patch"
            }
```

### Executes the scenario as a Gradle project

You can now simply run `./gradlew run` to execute your scenario.
After a couple of seconds, you will see the following report in your console:

```
============================================================
=====================  CAMPAIGN REPORT =====================
============================================================   

Campaign...........................quickstart-http-4fb6244cd3
Start..............................2022-03-30T16:41:02.597973Z
End................................2022-03-30T16:41:53.411602Z
Duration...........................50 seconds 
Configured minions.................10000
Completed minions..................10000
Successful steps executions........39832
Failed steps executions............42
Status.............................FAILED
   
        

=====================  SCENARIO REPORT =====================
Scenario...........................quickstart-http
Start..............................2022-03-30T16:41:02.597973Z
End................................2022-03-30T16:41:53.411602Z
Duration...........................50 seconds 
Configured minions.................10000
Completed minions..................10000
Successful steps executions........39832
Failed steps executions............42
Status.............................FAILED
Messages:
- ERROR:  step 'quickstart-http-post' - "Success: 9958, Execution errors: 42"
- INFO:   step 'verify-post' - "Success: 9958, Failures (verification errors): 0, Errors (execution errors): 0"
- INFO:   step '_622niod959' - "Success: 9958, Execution errors: 0"
- ERROR:  step 'verify-patch' - "Success: 9926, Failures (verification errors): 32, Errors (execution errors): 0"
        
```

You can note the information of the whole campaign as well as scenario by scenario.
There were errors, which explains why the execution failed. Do you want to know which errors? Go to the section
related to the events logging.

### Create an archive to transport your scenario and execute it

Execute the statement `./gradlew assemble`.

A JAR archive ending with `-qalipsis.jar` is created in the folder `build/libs`.
This archive contains all the dependencies and is self-sufficient to execute your scenario on any machine having
a Java Runtime Environment installed.

To execute it, open a terminal and go to the folder where your JAR archive is stored - move it out from the `build`
folder to avoid it to be deleted by mistake.

Execute the command `java -jar quickstart-<your-version>-qalipsis.jar`.

### Logging the events to know the details of the failures

In current directory, create a folder called `config`.
In this new folder, create a file called `qalipsis.yml` with the following content:

```yaml
events:
  root: warn
  export:
    slf4j:
      enabled: true
```

This enables the logging of the events with severity WARN and upper to a file called `qalipsis-events.log`.

Execute your scenario again, the events file is created and contains the details of the individual failed assertions:

```
2022-03-31T06:33:55.937Z  WARN --- step.assertion.failure;io.qalipsis.plugins.netty.tcp.ConnectionAndRequestResult@1e869276
MetersImpl(timeToSuccessfulConnect=null, timeToFailedConnect=null, timeToSuccessfulTlsConnect=null, timeToFailedTlsConnect=null, bytesCountToSend=85, sentBytes=85, timeToFirstByte=PT0.10696475S, timeToLastByte=PT0.1163375S, receivedBytes=394)
PT0.10696475S should be <= PT0.1S;campaign=quickstart-campaign-4d39026fd7,minion=quickstart-http-62qr1n87xx,scenario=quickstart-http,dag=dag-1,step=verify-patch,context-creation=2022-03-31T06:33:55.730Z,parent-step=_629qthims5,step-type=Verification,iteration=0,attempts-after-failure=0,isExhausted=true,isTail=true,isCompleted=true
2022-03-31T06:33:56.916Z  WARN --- step.assertion.failure;io.qalipsis.plugins.netty.tcp.ConnectionAndRequestResult@19a03b37
MetersImpl(timeToSuccessfulConnect=null, timeToFailedConnect=null, timeToSuccessfulTlsConnect=null, timeToFailedTlsConnect=null, bytesCountToSend=85, sentBytes=85, timeToFirstByte=PT0.128673083S, timeToLastByte=PT0.128737375S, receivedBytes=394)
PT0.128673083S should be <= PT0.1S;campaign=quickstart-campaign-4d39026fd7,minion=quickstart-http-62sqemkv8j,scenario=quickstart-http,dag=dag-1,step=verify-patch,context-creation=2022-03-31T06:33:56.735Z,parent-step=_629qthims5,step-type=Verification,iteration=0,attempts-after-failure=0,isExhausted=true,isTail=true,isCompleted=true
```

More specifically, you can messages like the following:
```
2022-03-31T06:33:55.937Z ... PT0.10696475S should be <= PT0.1S ... campaign=quickstart-campaign-4d39026fd7,... scenario=quickstart-http,... step=verify-patch
```
You can then exactly read:

- the exact instant when the failed assertion occurred
- the reason of the failure
- the campaign, scenario and verification step where it failed assertion occurred 

### Next steps

This quickstart ends here, and of course, log files are not the best to work with a massive amount of events, whether they are failures or not.

In the documentation of Qalipsis, you will discover how to push those events to databases, so that you can query and report them in a fancier way.