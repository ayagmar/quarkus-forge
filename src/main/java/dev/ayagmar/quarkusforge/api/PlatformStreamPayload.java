package dev.ayagmar.quarkusforge.api;

import java.util.List;

record PlatformStreamPayload(
    String key, String platformVersion, boolean recommended, List<String> javaVersions) {}
