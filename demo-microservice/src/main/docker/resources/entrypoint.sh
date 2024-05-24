#!/usr/bin/env bash
java -version

if [[ $JVM_ARGS != *"-Xss"* ]]
then
  JVM_ARGS="$JVM_ARGS -Xss1m"
fi

if [[ $JVM_ARGS != *"-XX:MaxMetaspaceSize"* ]]
then
  JVM_ARGS="$JVM_ARGS -XX:MaxMetaspaceSize=160m"
fi

if [[ $JVM_ARGS != *"-Xms"* ]]
then
  JVM_ARGS="$JVM_ARGS -Xms64m"
fi

if [[ $JVM_ARGS != *"-Xmx"* ]]
then
  JVM_ARGS="$JVM_ARGS -Xmx512m"
fi

if [[ JVM_ARGS != *"-Duser.timezone="* ]]
then
  JVM_ARGS="$JVM_ARGS -Duser.timezone=UTC"
fi

if [[ $JVM_ARGS != *"-Dfile.encoding="* ]]
then
  JVM_ARGS="$JVM_ARGS -Dfile.encoding=UTC-8"
fi

if [[ $JVM_ARGS != *"-Djava.io.tmpdir="* ]]
then
  JVM_ARGS="$JVM_ARGS -Djava.io.tmpdir=/tmp"
fi

if [[ $JVM_ARGS != *"-server"* ]]
then
  JVM_ARGS="-server $JVM_ARGS"
fi

echo "Starting with the arguments '$JVM_ARGS'"
java $JVM_ARGS -cp ".:/app/libs/*" START_CLASS $*