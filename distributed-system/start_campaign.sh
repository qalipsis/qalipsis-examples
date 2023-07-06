#!/bin/sh

#
# Copyright 2023 AERIS IT Solutions GmbH
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
# or implied. See the License for the specific language governing
# permissions and limitations under the License.
#


if [[ "$(find . -type d -name 'distributed-system-shadow*' -maxdepth 1)" == '' ]]
then
  echo "Unzipping the archive"
  cp build/distributions/*shadow*.zip .
  unzip *shadow*.zip
  rm -f *shadow*.zip
fi

qalipsis_folder=$(find . -type d -name 'distributed-system-shadow*' -maxdepth 1)
export JAVA_OPTS="-server -Xms1g -Xmx1g -XX:MaxMetaspaceSize=256m -XX:+UseG1GC -XX:MaxGCPauseMillis=100 -XX:G1ReservePercent=20 -Djava.security.egd=file:/dev/urandom -Dio.netty.eventLoopThreads=30"

if [[ $1 == "debug" ]]
then
  export JAVA_OPTS="$JAVA_OPTS -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
  shift
elif [[ $1 == "profile" || $1 == "profiler" ]]
then
  export JAVA_OPTS="$JAVA_OPTS -agentpath:/Applications/YourKit-Java-Profiler-2023.5.app/Contents/Resources/bin/mac/libyjpagent.dylib=disablestacktelemetry,exceptions=disable,delay=2000"
  shift
fi



$qalipsis_folder/bin/distributed-system --autostart $*
