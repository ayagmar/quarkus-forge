package dev.ayagmar.quarkusforge.diagnostics;

import dev.ayagmar.quarkusforge.api.JsonSupport;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class DiagnosticLogger {
  private final boolean enabled;
  private final String traceId;

  private DiagnosticLogger(boolean enabled, String traceId) {
    this.enabled = enabled;
    this.traceId = traceId;
  }

  public static DiagnosticLogger create(boolean enabled) {
    if (!enabled) {
      return new DiagnosticLogger(false, "");
    }
    return new DiagnosticLogger(true, UUID.randomUUID().toString());
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
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("ts", Instant.now().toString());
    payload.put("level", level);
    payload.put("event", event);
    payload.put("traceId", traceId);
    for (DiagnosticField field : fields) {
      payload.put(field.name(), field.value());
    }
    try {
      System.err.println(JsonSupport.writeString(payload));
    } catch (IOException ioException) {
      System.err.println(
          "{\"event\":\"diagnostic.encoding.failure\",\"traceId\":\""
              + traceId
              + "\",\"message\":\""
              + ioException.getMessage().replace("\"", "'")
              + "\"}");
    }
  }
}
