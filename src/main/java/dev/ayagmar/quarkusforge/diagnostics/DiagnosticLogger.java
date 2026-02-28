package dev.ayagmar.quarkusforge.diagnostics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.ayagmar.quarkusforge.api.ObjectMapperProvider;
import java.time.Instant;
import java.util.UUID;

public final class DiagnosticLogger {
  private final boolean enabled;
  private final String traceId;
  private final ObjectMapper objectMapper;

  private DiagnosticLogger(boolean enabled, String traceId, ObjectMapper objectMapper) {
    this.enabled = enabled;
    this.traceId = traceId;
    this.objectMapper = objectMapper;
  }

  public static DiagnosticLogger create(boolean enabled) {
    if (!enabled) {
      return new DiagnosticLogger(false, "", ObjectMapperProvider.shared());
    }
    return new DiagnosticLogger(true, UUID.randomUUID().toString(), ObjectMapperProvider.shared());
  }

  public void info(String event, DiagnosticField... fields) {
    log("INFO", event, fields);
  }

  public void error(String event, DiagnosticField... fields) {
    log("ERROR", event, fields);
  }

  private void log(String level, String event, DiagnosticField... fields) {
    if (!enabled) {
      return;
    }
    ObjectNode payload = objectMapper.createObjectNode();
    payload.put("ts", Instant.now().toString());
    payload.put("level", level);
    payload.put("event", event);
    payload.put("traceId", traceId);
    for (DiagnosticField field : fields) {
      payload.set(field.name(), objectMapper.valueToTree(field.value()));
    }
    try {
      System.err.println(objectMapper.writeValueAsString(payload));
    } catch (JsonProcessingException jsonProcessingException) {
      System.err.println(
          "{\"event\":\"diagnostic.encoding.failure\",\"traceId\":\""
              + traceId
              + "\",\"message\":\""
              + jsonProcessingException.getMessage().replace("\"", "'")
              + "\"}");
    }
  }
}
