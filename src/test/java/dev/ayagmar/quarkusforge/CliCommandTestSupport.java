package dev.ayagmar.quarkusforge;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

final class CliCommandTestSupport {
  private CliCommandTestSupport() {}

  static RuntimeConfig runtimeConfig(Path tempDir, URI baseUri) {
    return new RuntimeConfig(
        baseUri,
        tempDir.resolve("catalog-cache.json"),
        tempDir.resolve("favorites.json"),
        tempDir.resolve("preferences.json"));
  }

  static CommandResult runCommand(RuntimeConfig runtimeConfig, String... args) {
    PrintStream originalOut = System.out;
    PrintStream originalErr = System.err;
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    try {
      System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
      System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
      int exitCode = QuarkusForgeCli.runWithArgs(args, runtimeConfig);
      return new CommandResult(
          exitCode,
          stdout.toString(StandardCharsets.UTF_8),
          stderr.toString(StandardCharsets.UTF_8));
    } finally {
      System.setOut(originalOut);
      System.setErr(originalErr);
    }
  }

  static CommandResult runSmoke(RuntimeConfig runtimeConfig, boolean verbose) {
    PrintStream originalOut = System.out;
    PrintStream originalErr = System.err;
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    try {
      System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
      System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
      int exitCode = new QuarkusForgeCli(runtimeConfig).runSmokeForTest(verbose);
      return new CommandResult(
          exitCode,
          stdout.toString(StandardCharsets.UTF_8),
          stderr.toString(StandardCharsets.UTF_8));
    } finally {
      System.setOut(originalOut);
      System.setErr(originalErr);
    }
  }

  static void stubLiveMetadataWithMavenOnly() {
    stubLiveMetadataWithBuildTools("\"MAVEN\"");
  }

  static void stubLiveMetadataWithAllBuildTools() {
    stubLiveMetadataWithBuildTools("\"MAVEN\",\"GRADLE\",\"GRADLE_KOTLIN_DSL\"");
  }

  static void stubStreamsUnavailable() {
    stubFor(get(urlPathEqualTo("/api/streams")).willReturn(aResponse().withStatus(404)));
    stubFor(
        get(urlPathEqualTo("/q/openapi")).willReturn(aResponse().withStatus(200).withBody("{}")));
  }

  static void stubSingleRestExtensionCatalog() {
    stubFor(
        get(urlPathEqualTo("/api/extensions"))
            .willReturn(
                okJson(
                    """
                    [
                      {
                        "id":"io.quarkus:quarkus-rest",
                        "name":"REST",
                        "shortName":"rest",
                        "category":"Web",
                        "order":10
                      }
                    ]
                    """)));
  }

  private static void stubLiveMetadataWithBuildTools(String buildToolEnumValues) {
    stubFor(
        get(urlPathEqualTo("/api/streams"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        [
                          {
                            "key":"io.quarkus.platform:3.31",
                            "javaCompatibility": {
                              "versions":[17,21,25],
                              "recommended":25
                            },
                            "recommended":true,
                            "status":"FINAL"
                          }
                        ]
                        """)));

    stubFor(
        get(urlPathEqualTo("/q/openapi"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "paths": {
                            "/api/download": {
                              "get": {
                                "parameters": [
                                  {"name":"b","schema":{"enum":[%s]}}
                                ]
                              }
                            }
                          }
                        }
                        """
                            .formatted(buildToolEnumValues))));
  }

  record CommandResult(int exitCode, String standardOut, String standardError) {}
}
