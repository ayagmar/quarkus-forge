package dev.ayagmar.quarkusforge.headless;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ayagmar.quarkusforge.api.GenerationRequest;
import dev.ayagmar.quarkusforge.diagnostics.DiagnosticLogger;
import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HeadlessGenerationExecutionServiceTest {
  @TempDir Path tempDir;

  @Test
  void executeUsesResolvedProjectDirectoryAndGenerationRequest() throws Exception {
    StubProjectGenerator projectGenerator = new StubProjectGenerator();
    HeadlessGenerationExecutionService service =
        new HeadlessGenerationExecutionService(projectGenerator);

    ProjectRequest request =
        new ProjectRequest(
            "com.example",
            "demo-app",
            "1.0.0-SNAPSHOT",
            "com.example.demo",
            tempDir.toString(),
            "io.quarkus.platform:3.31",
            "maven",
            "21");

    Path generatedProjectRoot =
        service.execute(
            request,
            java.util.List.of("io.quarkus:quarkus-rest"),
            Duration.ofSeconds(5),
            DiagnosticLogger.create(false));

    assertThat(generatedProjectRoot).isEqualTo(Path.of("output/demo-app"));
    assertThat(projectGenerator.lastOutputPath).isEqualTo(tempDir.resolve("demo-app"));
    assertThat(projectGenerator.lastRequest)
        .isEqualTo(
            new GenerationRequest(
                "com.example",
                "demo-app",
                "1.0.0-SNAPSHOT",
                "io.quarkus.platform:3.31",
                "maven",
                "21",
                java.util.List.of("io.quarkus:quarkus-rest")));
  }

  @Test
  void executeCancelsRunningFutureOnTimeout() {
    StubProjectGenerator projectGenerator = new StubProjectGenerator();
    projectGenerator.generationFuture = new CompletableFuture<>();
    HeadlessGenerationExecutionService service =
        new HeadlessGenerationExecutionService(projectGenerator);

    ProjectRequest request =
        new ProjectRequest(
            "com.example",
            "demo-app",
            "1.0.0-SNAPSHOT",
            "com.example.demo",
            tempDir.toString(),
            "io.quarkus.platform:3.31",
            "maven",
            "21");

    assertThatThrownBy(
            () ->
                service.execute(
                    request,
                    java.util.List.of("io.quarkus:quarkus-rest"),
                    Duration.ofMillis(1),
                    DiagnosticLogger.create(false)))
        .isInstanceOf(java.util.concurrent.TimeoutException.class);
    assertThat(projectGenerator.generationFuture.isCancelled()).isTrue();
    assertThat(projectGenerator.cancelled.getAsBoolean()).isTrue();
  }

  private static final class StubProjectGenerator implements HeadlessProjectGenerator {
    CompletableFuture<Path> generationFuture =
        CompletableFuture.completedFuture(Path.of("output/demo-app"));
    GenerationRequest lastRequest;
    Path lastOutputPath;
    BooleanSupplier cancelled = () -> false;

    @Override
    public CompletableFuture<Path> startGeneration(
        GenerationRequest generationRequest,
        Path outputPath,
        BooleanSupplier cancelled,
        Consumer<String> progressLineConsumer) {
      lastRequest = generationRequest;
      lastOutputPath = outputPath;
      this.cancelled = cancelled;
      return generationFuture;
    }
  }
}
