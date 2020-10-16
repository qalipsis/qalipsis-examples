#!/bin/sh
docker build . -t qalipsis-demo-app
if [ $? -eq 0 ]; then
  echo
  echo
  echo "To run the docker container execute:"
  echo "    $ docker run qalipsis-demo-app"
else
  echo "An error occurred" >&2
  exit 1
fi