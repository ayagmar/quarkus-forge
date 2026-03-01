package dev.ayagmar.quarkusforge.api;

record StreamPayload(
    String key,
    String platformVersion,
    boolean recommended,
    JavaCompatibilityPayload javaCompatibility) {}
