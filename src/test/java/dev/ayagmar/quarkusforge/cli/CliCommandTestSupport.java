package dev.ayagmar.quarkusforge.cli;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.ayagmar.quarkusforge.runtime.RuntimeConfig;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.Callable;

public final class CliCommandTestSupport {
  private static final Object STREAM_LOCK = new Object();

  private CliCommandTestSupport() {}

  public static RuntimeConfig runtimeConfig(Path tempDir, URI baseUri) {
    return new RuntimeConfig(
        baseUri,
        tempDir.resolve("catalog-cache.json"),
        tempDir.resolve("favorites.json"),
        tempDir.resolve("preferences.json"));
  }

  public static CommandResult runCommand(RuntimeConfig runtimeConfig, String... args) {
    return captureCommandOutput(() -> QuarkusForgeCli.runWithArgs(args, runtimeConfig));
  }

  public static CommandResult runHeadlessCommand(RuntimeConfig runtimeConfig, String... args) {
    return captureCommandOutput(() -> HeadlessCli.runWithArgs(args, runtimeConfig));
  }

  public static CommandResult runSmoke(RuntimeConfig runtimeConfig, boolean verbose) {
    return captureCommandOutput(() -> new QuarkusForgeCli(runtimeConfig).runSmokeForTest(verbose));
  }

  private static CommandResult captureCommandOutput(Callable<Integer> action) {
    synchronized (STREAM_LOCK) {
      PrintStream originalOut = System.out;
      PrintStream originalErr = System.err;
      ByteArrayOutputStream stdout = new ByteArrayOutputStream();
      ByteArrayOutputStream stderr = new ByteArrayOutputStream();
      try {
        System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
        int exitCode = action.call();
        return new CommandResult(
            exitCode,
            stdout.toString(StandardCharsets.UTF_8),
            stderr.toString(StandardCharsets.UTF_8));
      } catch (Exception exception) {
        throw new RuntimeException(exception);
      } finally {
        System.setOut(originalOut);
        System.setErr(originalErr);
      }
    }
  }

  public static void stubLiveMetadataWithMavenOnly(WireMockServer wireMockServer) {
    stubLiveMetadataWithBuildTools(wireMockServer, "\"MAVEN\"");
  }

  public static void stubLiveMetadataWithAllBuildTools(WireMockServer wireMockServer) {
    stubLiveMetadataWithBuildTools(wireMockServer, "\"MAVEN\",\"GRADLE\",\"GRADLE_KOTLIN_DSL\"");
  }

  public static void stubStreamsUnavailable(WireMockServer wireMockServer) {
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/streams")).willReturn(aResponse().withStatus(404)));
    wireMockServer.stubFor(
        get(urlPathEqualTo("/q/openapi")).willReturn(aResponse().withStatus(200).withBody("{}")));
  }

  public static void stubSingleRestExtensionCatalog(WireMockServer wireMockServer) {
    wireMockServer.stubFor(
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

  private static void stubLiveMetadataWithBuildTools(
      WireMockServer wireMockServer, String buildToolEnumValues) {
    wireMockServer.stubFor(
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

    wireMockServer.stubFor(
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

  public record CommandResult(int exitCode, String standardOut, String standardError) {}
}
