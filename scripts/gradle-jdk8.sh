#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
JAVA_HOME="${TRAINER_JAVA_HOME:-$(/usr/libexec/java_home -v 1.8)}"
export JAVA_HOME
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$PWD/.gradle-home}"
exec ./gradlew "$@"
