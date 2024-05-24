FROM --platform=$TARGETPLATFORM eclipse-temurin:21-jre-alpine

ARG JAR_NAME
ARG START_CLASS

WORKDIR /app

RUN apk add --no-cache tini bash curl busybox-extras

RUN mkdir -p /app/libs /app/logs /app/config
ADD libs /app/libs
ADD $JAR_NAME /app/libs

RUN chmod -R 777 /app

COPY entrypoint.sh /usr/local/bin
RUN sed -i "s/START_CLASS/$START_CLASS/g" "/usr/local/bin/entrypoint.sh"

RUN chmod +x /usr/local/bin/entrypoint.sh

EXPOSE 8080
EXPOSE 8081
EXPOSE 5005

ENV LOG_PATH /app/logs
ENV MIN_FLAT_FILE_LOG_LEVEL ALL
ENTRYPOINT ["/sbin/tini", "--", "/usr/local/bin/entrypoint.sh"]