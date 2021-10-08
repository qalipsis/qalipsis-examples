FROM openjdk:11-jdk-slim
LABEL maintainer="Eric Jessé <eric.jesse@aeris-consulting.com>"

ARG JAR_NAME

ENV TINI_VERSION v0.19.0
ADD https://github.com/krallin/tini/releases/download/${TINI_VERSION}/tini /usr/local/bin/tini
RUN chmod +x /usr/local/bin/tini

RUN mkdir -p /opt/demo && chmod 777 /opt/demo
COPY $JAR_NAME /opt/demo/qalipsis-demo-microservice.jar
RUN chmod +x /opt/demo/qalipsis-demo-microservice.jar

COPY entrypoint.sh /usr/local/bin
RUN chmod +x /usr/local/bin/entrypoint.sh

ENV JVM_ARGS "-Xms64m -Xmx256m"

ENTRYPOINT ["/usr/local/bin/tini", "--", "/usr/local/bin/entrypoint.sh"]

EXPOSE 8080 8443