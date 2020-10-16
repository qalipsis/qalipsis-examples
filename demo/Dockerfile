FROM oracle/graalvm-ce:20.2.0-java11 as graalvm
RUN gu install native-image

COPY . /home/app/qalipsis
WORKDIR /home/app/qalipsis

RUN native-image --no-server --no-fallback \
  -H:IncludeResources=.* \
  -H:+TraceClassInitialization \
  -H:ReflectionConfigurationFiles=reflect.json \
  -H:Name=qalipsis-sample-simple \
  -H:Class=io.qalipsis.runtime.Qalipsis \
  -cp build/libs/*-qalipsis.jar

FROM frolvlad/alpine-glibc
RUN apk update && apk add libstdc++
EXPOSE 8080
COPY --from=graalvm /home/app/qalipsis/qalipsis /app/qalipsis
ENTRYPOINT ["/app/qalipsis"]