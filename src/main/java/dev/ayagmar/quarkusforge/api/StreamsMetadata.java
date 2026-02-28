package dev.ayagmar.quarkusforge.api;

import java.util.List;

record StreamsMetadata(List<String> javaVersions, List<PlatformStream> platformStreams) {
  StreamsMetadata {
    javaVersions = List.copyOf(javaVersions);
    platformStreams = List.copyOf(platformStreams);
  }
}
