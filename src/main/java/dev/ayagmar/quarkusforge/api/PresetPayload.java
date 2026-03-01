package dev.ayagmar.quarkusforge.api;

import java.util.List;

record PresetPayload(String key, String title, String icon, List<String> extensions) {}
