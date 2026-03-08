package dev.ayagmar.quarkusforge.headless;

import static dev.ayagmar.quarkusforge.diagnostics.DiagnosticField.of;

import dev.ayagmar.quarkusforge.api.GenerationRequest;
import dev.ayagmar.quarkusforge.diagnostics.DiagnosticLogger;
import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

final class HeadlessGenerationExecutionService {
  private final HeadlessProjectGenerator projectGenerator;

  HeadlessGenerationExecutionService(HeadlessProjectGenerator projectGenerator) {
    this.projectGenerator = Objects.requireNonNull(projectGenerator);
  }

  Path execute(
      ProjectRequest request,
      List<String> extensionIds,
      Duration generationTimeout,
      DiagnosticLogger diagnostics)
      throws Exception {
    Path outputPath = HeadlessOutputPrinter.resolveProjectDirectory(request);
    GenerationRequest generationRequest =
        new GenerationRequest(
            request.groupId(),
            request.artifactId(),
            request.version(),
            request.platformStream(),
            request.buildTool(),
            request.javaVersion(),
            extensionIds);

    diagnostics.info(
        "generate.execute.start",
        of("outputPath", outputPath.toString()),
        of("extensionCount", extensionIds.size()));

    CompletableFuture<Path> generationFuture = null;
    AtomicBoolean cancelled = new AtomicBoolean(false);
    try {
      generationFuture =
          projectGenerator.startGeneration(
              generationRequest, outputPath, cancelled::get, System.out::println);
      Path generatedProjectRoot =
          generationFuture.get(generationTimeout.toMillis(), TimeUnit.MILLISECONDS);
      diagnostics.info(
          "generate.execute.success", of("projectRoot", generatedProjectRoot.toString()));
      return generatedProjectRoot;
    } catch (Exception exception) {
      if (exception instanceof TimeoutException && generationFuture != null) {
        cancelled.set(true);
        generationFuture.cancel(true);
      }
      throw exception;
    }
  }
}
