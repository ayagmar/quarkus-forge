package dev.ayagmar.quarkusforge.api;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class ObjectMapperProvider {
  private static final ObjectMapper SHARED = new ObjectMapper();

  private ObjectMapperProvider() {}

  public static ObjectMapper shared() {
    return SHARED;
  }
}
