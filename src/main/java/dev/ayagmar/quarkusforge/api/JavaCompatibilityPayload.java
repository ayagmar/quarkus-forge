package dev.ayagmar.quarkusforge.api;

import java.util.List;

record JavaCompatibilityPayload(List<Integer> versions, Integer recommended) {}
