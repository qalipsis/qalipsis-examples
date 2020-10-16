FROM oracle/graalvm-ce:1.0.0-rc15 as graalvm
COPY . /home/app/qalipsis-samples-simple
WORKDIR /home/app/qalipsis-samples-simple

RUN native-image --no-server --no-fallback \
  -H:+TraceClassInitialization \
  -cp build/libs/*-qalipsis.jar

FROM frolvlad/alpine-glibc
EXPOSE 8080
COPY --from=graalvm /home/app/qalipsis-samples-simple .
ENTRYPOINT ["./qalipsis-samples-simple"]
