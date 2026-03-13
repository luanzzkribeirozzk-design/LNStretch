#!/usr/bin/env sh
##############################################################################
# Gradle start up script for UN*X
##############################################################################
APP_NAME="Gradle"
APP_BASE_NAME=$(basename "$0")
MAX_FD="maximum"
warn() { echo "$*"; }
die() { echo; echo "$*"; echo; exit 1; }
SAVED=$(pwd)
cd "$(dirname "$0")/" || exit 1
APP_HOME=$(pwd -P)
cd "$SAVED" || exit 1
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi
exec "$JAVACMD"     -classpath "$CLASSPATH"     org.gradle.wrapper.GradleWrapperMain     "$@"