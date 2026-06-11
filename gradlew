#!/bin/sh
#
# Gradle wrapper script — do not edit manually

APP_HOME=$(dirname "$(readlink -f "$0")" 2>/dev/null || dirname "$(realpath "$0")" 2>/dev/null || cd "$(dirname "$0")" && pwd)
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

exec "$JAVA_HOME/bin/java" $DEFAULT_JVM_OPTS -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
