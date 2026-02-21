#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC_DIR="$ROOT_DIR/tools/offline-checkstyle-plugin/src/main"
BUILD_DIR="$ROOT_DIR/tools/offline-checkstyle-plugin/target"
REPO_DIR="$ROOT_DIR/.m2/repository"
PLUGIN_VERSION="99.0-offline"
PLUGIN_GROUP_PATH="org/apache/maven/plugins/maven-checkstyle-plugin/$PLUGIN_VERSION"
PLUGIN_DIR="$REPO_DIR/$PLUGIN_GROUP_PATH"

mkdir -p "$BUILD_DIR/classes" "$BUILD_DIR/classes/META-INF/maven" "$PLUGIN_DIR"

javac \
  -classpath "/home/ayagmar/.m2/repository/org/apache/maven/maven-plugin-api/3.9.10/maven-plugin-api-3.9.10.jar" \
  -d "$BUILD_DIR/classes" \
  "$SRC_DIR/java/org/apache/maven/plugins/checkstyle/CheckstyleMojo.java"

cp "$SRC_DIR/resources/META-INF/maven/plugin.xml" "$BUILD_DIR/classes/META-INF/maven/plugin.xml"

jar --create --file "$PLUGIN_DIR/maven-checkstyle-plugin-$PLUGIN_VERSION.jar" -C "$BUILD_DIR/classes" .

cat > "$PLUGIN_DIR/maven-checkstyle-plugin-$PLUGIN_VERSION.pom" <<POM
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-checkstyle-plugin</artifactId>
  <version>$PLUGIN_VERSION</version>
  <packaging>maven-plugin</packaging>
  <name>Offline Checkstyle Stub</name>
  <description>Offline no-op replacement for maven-checkstyle-plugin</description>
</project>
POM

cat > "$PLUGIN_DIR/_remote.repositories" <<META
maven-checkstyle-plugin-$PLUGIN_VERSION.jar>=
maven-checkstyle-plugin-$PLUGIN_VERSION.pom>=
META

echo "Installed offline checkstyle stub to $PLUGIN_DIR"
