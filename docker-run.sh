#!/bin/bash -e

DOCKER_IMAGE="oss-review-toolkit:latest"
DOCKER_OPTIONS="--rm"

#DOCKER_OPTIONS="--rm -u $(id -u $USER):$(id -g $USER)"
#DOCKER_MOUNTS="-v /etc/group:/etc/group:ro -v /etc/passwd:/etc/passwd:ro"

docker run $DOCKER_OPTIONS $DOCKER_MOUNTS $DOCKER_IMAGE "$@"
