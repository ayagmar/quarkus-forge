package dev.ayagmar.quarkusforge.api;

import java.util.List;

record OpenApiGetPayload(List<OpenApiParameterPayload> parameters) {}
