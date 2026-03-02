package dev.ayagmar.quarkusforge;

import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import dev.ayagmar.quarkusforge.domain.ValidationReport;
import java.nio.file.Path;
import java.util.List;

/** Formats headless-mode output to stdout/stderr. No CLI state dependency. */
final class HeadlessOutputPrinter {
  private HeadlessOutputPrinter() {}

  static void printValidationErrors(
      ValidationReport validation, String sourceLabel, String sourceDetail) {
    System.err.println("Input validation failed:");
    if (sourceLabel != null && !sourceLabel.isBlank()) {
      System.err.println(" - metadataSource: " + sourceLabel);
    }
    if (sourceDetail != null && !sourceDetail.isBlank()) {
      System.err.println(" - metadataDetail: " + sourceDetail);
    }
    for (var error : validation.errors()) {
      System.err.println(" - " + error.field() + ": " + error.message());
    }
  }

  static void printPrefillSummary(ProjectRequest request, String sourceLabel, String sourceDetail) {
    System.out.println("Prefill validated successfully:");
    printRequestFields(request);
    if (sourceLabel != null && !sourceLabel.isBlank()) {
      System.out.println(" - metadataSource: " + sourceLabel);
    }
    if (sourceDetail != null && !sourceDetail.isBlank()) {
      System.out.println(" - metadataDetail: " + sourceDetail);
    }
    System.out.println(" - generatedProjectDirectory: " + resolveProjectDirectory(request));
  }

  static void printDryRunSummary(
      ProjectRequest request, List<String> extensionIds, String sourceLabel) {
    System.out.println("Dry-run validated successfully:");
    printRequestFields(request);
    System.out.println(" - extensions: " + extensionIds);
    String effectiveSourceLabel =
        (sourceLabel == null || sourceLabel.isBlank()) ? "unknown" : sourceLabel;
    System.out.println(" - catalogSource: " + effectiveSourceLabel);
    System.out.println(" - generatedProjectDirectory: " + resolveProjectDirectory(request));
  }

  private static void printRequestFields(ProjectRequest request) {
    System.out.println(" - groupId: " + request.groupId());
    System.out.println(" - artifactId: " + request.artifactId());
    System.out.println(" - version: " + request.version());
    System.out.println(" - packageName: " + request.packageName());
    System.out.println(" - outputDirectory: " + request.outputDirectory());
    System.out.println(" - platformStream: " + request.platformStream());
    System.out.println(" - buildTool: " + request.buildTool());
    System.out.println(" - javaVersion: " + request.javaVersion());
  }

  static Path resolveProjectDirectory(ProjectRequest request) {
    return Path.of(request.outputDirectory()).resolve(request.artifactId()).normalize();
  }
}
