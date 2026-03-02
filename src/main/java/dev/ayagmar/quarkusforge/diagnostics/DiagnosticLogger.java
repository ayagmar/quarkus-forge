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

  public void warn(String event, DiagnosticField... fields) {
    log("WARN", event, fields);
  }

  public void error(String event, DiagnosticField... fields) {
    log("ERROR", event, fields);
  }

  private void log(String level, String event, DiagnosticField... fields) {
    // INFO events are gated behind --verbose; WARN and ERROR always emit to stderr.
    if (!enabled && level.equals("INFO")) {
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
      String message = ioException.getMessage();
      String safeMessage = message == null ? "unknown" : escapeJsonValue(message);
      System.err.println(
          "{\"event\":\"diagnostic.encoding.failure\",\"traceId\":\""
              + traceId
              + "\",\"message\":\""
              + safeMessage
              + "\"}");
    }
  }

  private static String escapeJsonValue(String value) {
    StringBuilder sb = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      char ch = value.charAt(i);
      switch (ch) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> {
          if (ch < 0x20) {
            sb.append(String.format("\\u%04x", (int) ch));
          } else {
            sb.append(ch);
          }
        }
      }
    }
    return sb.toString();
  }
}
