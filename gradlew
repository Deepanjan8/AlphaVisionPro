#!/bin/sh
##
## Gradle start up script for UN*X
##
GRADLE_APP_ARGS="$@"
APP_HOME="$(cd "$(dirname "$0")" && pwd)"
JAVA_OPTS="$JAVA_OPTS -Xmx64m -Xms64m"
exec java $JAVA_OPTS \
     -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
     org.gradle.wrapper.GradleWrapperMain "$@"
