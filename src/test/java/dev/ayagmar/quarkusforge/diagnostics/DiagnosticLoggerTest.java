package dev.ayagmar.quarkusforge.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

@ResourceLock(Resources.SYSTEM_ERR)
class DiagnosticLoggerTest {
  private PrintStream originalErr;
  private ByteArrayOutputStream stderr;

  @BeforeEach
  void captureStderr() {
    originalErr = System.err;
    stderr = new ByteArrayOutputStream();
    System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
  }

  @AfterEach
  void restoreStderr() {
    System.setErr(originalErr);
  }

  private String capturedStderr() {
    return stderr.toString(StandardCharsets.UTF_8);
  }

  @Test
  void disabledLoggerSuppressesInfoMessages() {
    DiagnosticLogger logger = DiagnosticLogger.create(false);
    logger.info("test.event", DiagnosticField.of("key", "value"));

    assertThat(capturedStderr()).isEmpty();
  }

  @Test
  void disabledLoggerEmitsWarnMessages() {
    DiagnosticLogger logger = DiagnosticLogger.create(false);
    logger.warn("test.warn", DiagnosticField.of("detail", "something"));

    assertThat(capturedStderr()).contains("WARN").contains("test.warn");
  }

  @Test
  void disabledLoggerEmitsErrorMessages() {
    DiagnosticLogger logger = DiagnosticLogger.create(false);
    logger.error("test.error", DiagnosticField.of("detail", "oops"));

    assertThat(capturedStderr()).contains("ERROR").contains("test.error");
  }

  @Test
  void enabledLoggerEmitsInfoMessages() {
    DiagnosticLogger logger = DiagnosticLogger.create(true);
    logger.info("test.info", DiagnosticField.of("count", 42));

    String output = capturedStderr();
    assertThat(output).contains("INFO").contains("test.info").contains("42");
  }

  @Test
  void enabledLoggerIncludesTraceIdAndTimestamp() {
    DiagnosticLogger logger = DiagnosticLogger.create(true);
    logger.info("ts.check");

    String output = capturedStderr();
    assertThat(output).contains("\"traceId\"").contains("\"ts\"");
  }

  @Test
  void logOutputIncludesCustomFields() {
    DiagnosticLogger logger = DiagnosticLogger.create(true);
    logger.error("multi.field", DiagnosticField.of("a", "alpha"), DiagnosticField.of("b", 99));

    String output = capturedStderr();
    assertThat(output).contains("\"a\"").contains("alpha").contains("\"b\"").contains("99");
  }

  @Test
  void enabledLoggerEmitsWarnWithTimestamp() {
    DiagnosticLogger logger = DiagnosticLogger.create(true);
    logger.warn("warn.event", DiagnosticField.of("reason", "stale"));

    String output = capturedStderr();
    assertThat(output).contains("WARN").contains("warn.event").contains("stale");
  }

  @Test
  void enabledLoggerWarnIncludesTraceId() {
    DiagnosticLogger logger = DiagnosticLogger.create(true);
    logger.warn("trace.test");

    assertThat(capturedStderr()).contains("traceId");
  }

  @Test
  void disabledLoggerTraceIdIsEmpty() {
    DiagnosticLogger logger = DiagnosticLogger.create(false);
    logger.error("err.event");

    // Disabled logger has empty traceId but still outputs errors
    assertThat(capturedStderr()).contains("\"traceId\":\"\"");
  }

  @Test
  void enabledLoggerTraceIdIsUuid() {
    DiagnosticLogger logger = DiagnosticLogger.create(true);
    logger.info("uuid.check");

    String output = capturedStderr();
    // UUID pattern: 8-4-4-4-12 hex chars
    assertThat(output)
        .containsPattern("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
  }

  @Test
  void multipleLogCallsFromSameLoggerUseSameTraceId() {
    DiagnosticLogger logger = DiagnosticLogger.create(true);
    logger.info("first");
    logger.info("second");

    String output = capturedStderr();
    String[] lines = output.strip().split("\n");
    assertThat(lines).hasSize(2);

    Pattern traceIdPattern =
        Pattern.compile(
            "\"traceId\":\"([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\"");
    Matcher matcher1 = traceIdPattern.matcher(lines[0]);
    Matcher matcher2 = traceIdPattern.matcher(lines[1]);
    assertThat(matcher1.find()).as("first line should contain a UUID traceId").isTrue();
    assertThat(matcher2.find()).as("second line should contain a UUID traceId").isTrue();
    assertThat(matcher1.group(1)).isEqualTo(matcher2.group(1));
  }

  @Test
  void infoWithNoFieldsEmitsValidJson() {
    DiagnosticLogger logger = DiagnosticLogger.create(true);
    logger.info("no.fields");

    String output = capturedStderr();
    assertThat(output).contains("\"event\":\"no.fields\"");
    assertThat(output).contains("\"level\":\"INFO\"");
  }

  @Test
  void errorWithMultipleFieldsPreservesOrder() {
    DiagnosticLogger logger = DiagnosticLogger.create(true);
    logger.error("ordered", DiagnosticField.of("first", 1), DiagnosticField.of("second", 2));

    String output = capturedStderr();
    int firstIdx = output.indexOf("\"first\"");
    int secondIdx = output.indexOf("\"second\"");
    assertThat(firstIdx).isLessThan(secondIdx);
  }

  // ── escapeJsonValue via field values containing special chars ─────

  @Test
  void logEscapesDoubleQuoteInFieldValue() {
    DiagnosticLogger logger = DiagnosticLogger.create(true);
    logger.info("quote.test", DiagnosticField.of("msg", "say \"hello\""));

    String output = capturedStderr();
    assertThat(output).contains("say \\\"hello\\\"");
  }

  @Test
  void logEscapesBackslashInFieldValue() {
    DiagnosticLogger logger = DiagnosticLogger.create(true);
    logger.info("bs.test", DiagnosticField.of("path", "C:\\Users\\dir"));

    String output = capturedStderr();
    assertThat(output).contains("C:\\\\Users\\\\dir");
  }

  @Test
  void logEscapesNewlineInFieldValue() {
    DiagnosticLogger logger = DiagnosticLogger.create(true);
    logger.info("nl.test", DiagnosticField.of("msg", "line1\nline2"));

    String output = capturedStderr();
    assertThat(output).contains("line1\\nline2");
  }

  @Test
  void logEscapesCarriageReturnInFieldValue() {
    DiagnosticLogger logger = DiagnosticLogger.create(true);
    logger.info("cr.test", DiagnosticField.of("msg", "line1\rline2"));

    String output = capturedStderr();
    assertThat(output).contains("line1\\rline2");
  }

  @Test
  void logEscapesTabInFieldValue() {
    DiagnosticLogger logger = DiagnosticLogger.create(true);
    logger.info("tab.test", DiagnosticField.of("msg", "col1\tcol2"));

    String output = capturedStderr();
    assertThat(output).contains("col1\\tcol2");
  }

  @Test
  void logEscapesControlCharactersBelow0x20() {
    DiagnosticLogger logger = DiagnosticLogger.create(true);
    // Bell character (0x07) - should be escaped as \u0007
    logger.info("ctrl.test", DiagnosticField.of("msg", "beep\u0007end"));

    String output = capturedStderr();
    assertThat(output).contains("beep\\u0007end");
  }
}
