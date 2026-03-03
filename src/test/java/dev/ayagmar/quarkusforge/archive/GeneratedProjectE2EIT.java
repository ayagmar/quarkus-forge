package dev.ayagmar.quarkusforge.archive;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.havingExactly;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.ayagmar.quarkusforge.api.GenerationRequest;
import dev.ayagmar.quarkusforge.api.QuarkusApiClient;
import dev.ayagmar.quarkusforge.api.RetryPolicy;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeneratedProjectE2EIT {
  private static final Duration DEV_MODE_START_TIMEOUT = Duration.ofMinutes(5);

  @TempDir Path tempDir;

  private WireMockServer wireMockServer;

  @BeforeEach
  void setUp() {
    wireMockServer = new WireMockServer(0);
    wireMockServer.start();
    com.github.tomakehurst.wiremock.client.WireMock.configureFor(
        "localhost", wireMockServer.port());
  }

  @AfterEach
  void tearDown() {
    if (wireMockServer != null) {
      wireMockServer.stop();
    }
  }

  @Test
  void generatesProjectFromRepresentativeSelectionAndStartsInDevMode() throws Exception {
    String artifactId = "forge-e2e-app";
    GenerationRequest request =
        new GenerationRequest(
            "org.acme",
            artifactId,
            "1.0.0-SNAPSHOT",
            "maven",
            "25",
            List.of("io.quarkus:quarkus-resteasy-reactive", "io.quarkus:quarkus-arc"));

    stubFor(
        get(urlPathEqualTo("/api/download"))
            .willReturn(
                aResponse().withStatus(200).withBody(createGeneratedProjectZip(artifactId))));

    ProjectArchiveService archiveService =
        new ProjectArchiveService(newClient(), new SafeZipExtractor());
    List<ProjectArchiveService.ProgressStep> progressSteps = new CopyOnWriteArrayList<>();

    Path generatedProjectRoot = tempDir.resolve("generated").resolve(artifactId);
    Path generatedProject =
        archiveService
            .downloadAndExtract(
                request,
                generatedProjectRoot,
                OverwritePolicy.FAIL_IF_EXISTS,
                () -> false,
                progressSteps::add)
            .join();

    assertThat(generatedProject).isEqualTo(generatedProjectRoot);
    assertThat(progressSteps)
        .containsExactly(
            ProjectArchiveService.ProgressStep.REQUESTING_ARCHIVE,
            ProjectArchiveService.ProgressStep.EXTRACTING_ARCHIVE);

    verify(
        getRequestedFor(urlPathEqualTo("/api/download"))
            .withQueryParam("g", equalTo("org.acme"))
            .withQueryParam("a", equalTo(artifactId))
            .withQueryParam("v", equalTo("1.0.0-SNAPSHOT"))
            .withQueryParam("b", equalTo("MAVEN"))
            .withQueryParam("j", equalTo("25"))
            .withQueryParam(
                "e",
                havingExactly(
                    equalTo("io.quarkus:quarkus-resteasy-reactive"),
                    equalTo("io.quarkus:quarkus-arc"))));

    assertThat(generatedProject.resolve("pom.xml")).exists();
    assertThat(generatedProject.resolve("README.md")).exists();
    assertThat(generatedProject.resolve("src/main/resources/application.properties")).exists();
    assertThat(generatedProject.resolve("src/main/java/org/acme/GreetingResource.java")).exists();

    assertStartsInDevMode(generatedProject);
  }

  private QuarkusApiClient newClient() {
    return new QuarkusApiClient(
        HttpClient.newHttpClient(),
        URI.create(wireMockServer.baseUrl()),
        new RetryPolicy(1, Duration.ofSeconds(10), Duration.ofMillis(1), 0.0d),
        delay -> java.util.concurrent.CompletableFuture.completedFuture(null),
        Clock.fixed(Instant.parse("2026-02-22T00:00:00Z"), ZoneOffset.UTC),
        () -> 0.5d);
  }

  private static void assertStartsInDevMode(Path generatedProject) throws Exception {
    String mavenCommand = isWindowsOs() ? "mvn.cmd" : "mvn";
    ProcessBuilder processBuilder =
        new ProcessBuilder(
            mavenCommand,
            "-DskipTests",
            "-Dquarkus.console.enabled=false",
            "-Dquarkus.enforceBuildGoal=false",
            "-Dquarkus.http.host=127.0.0.1",
            "-Dquarkus.http.port=0",
            "quarkus:dev");
    processBuilder.directory(generatedProject.toFile());
    processBuilder.redirectErrorStream(true);

    Process process = processBuilder.start();
    List<String> outputLines = Collections.synchronizedList(new ArrayList<>());
    long deadlineNanos = System.nanoTime() + DEV_MODE_START_TIMEOUT.toNanos();
    AtomicBoolean started = new AtomicBoolean(false);
    AtomicBoolean outputClosed = new AtomicBoolean(false);
    Thread outputReader =
        Thread.ofPlatform()
            .name("generated-project-e2e-output-reader")
            .daemon()
            .unstarted(
                () -> {
                  try (BufferedReader reader =
                      new BufferedReader(
                          new InputStreamReader(
                              process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                      outputLines.add(line);
                      if (isDevModeReadyLine(line)) {
                        started.set(true);
                      }
                    }
                  } catch (IOException ignored) {
                    // Stream may close as process exits.
                  } finally {
                    outputClosed.set(true);
                  }
                });
    outputReader.start();

    boolean exitedBeforeReady = false;
    Integer earlyExitCode = null;
    try {
      while (System.nanoTime() < deadlineNanos && !started.get()) {
        if (!process.isAlive() && outputClosed.get()) {
          exitedBeforeReady = true;
          earlyExitCode = process.exitValue();
          break;
        }
        TimeUnit.MILLISECONDS.sleep(100);
      }
    } finally {
      terminateProcess(process);
      outputReader.join(5_000);
    }

    String exitDetail =
        exitedBeforeReady
            ? " (process exited early with code " + earlyExitCode + ")"
            : " (process did not report readiness in time)";
    assertThat(started.get())
        .withFailMessage(
            "Generated project did not start in dev mode within %s%s. Last output:%n%s",
            DEV_MODE_START_TIMEOUT, exitDetail, tail(outputLines, 80))
        .isTrue();
  }

  private static boolean isWindowsOs() {
    String osName = System.getProperty("os.name", "");
    return osName.toLowerCase(java.util.Locale.ROOT).contains("win");
  }

  private static boolean isDevModeReadyLine(String line) {
    return line.contains("Listening on:")
        || line.contains("Profile dev activated.")
        || line.contains("Press [h] for more options")
        || line.contains("Installed features:");
  }

  private static void terminateProcess(Process process) throws InterruptedException {
    ProcessHandle rootHandle = process.toHandle();
    terminateProcessTree(rootHandle, false);
    if (!waitForProcessExit(process, 10)) {
      terminateProcessTree(rootHandle, true);
      waitForProcessExit(process, 10);
    }
  }

  private static void terminateProcessTree(ProcessHandle rootHandle, boolean force) {
    List<ProcessHandle> descendants = rootHandle.descendants().toList();
    for (int index = descendants.size() - 1; index >= 0; index--) {
      terminateHandle(descendants.get(index), force);
    }
    terminateHandle(rootHandle, force);
  }

  private static void terminateHandle(ProcessHandle handle, boolean force) {
    if (!handle.isAlive()) {
      return;
    }
    if (force) {
      handle.destroyForcibly();
    } else {
      handle.destroy();
    }
  }

  private static boolean waitForProcessExit(Process process, long timeoutSeconds)
      throws InterruptedException {
    return process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
  }

  private static String tail(List<String> lines, int maxLines) {
    if (lines.isEmpty()) {
      return "<no output>";
    }
    int fromIndex = Math.max(0, lines.size() - maxLines);
    return String.join(System.lineSeparator(), lines.subList(fromIndex, lines.size()));
  }

  private byte[] createGeneratedProjectZip(String artifactId) throws IOException {
    Map<String, byte[]> entries = new LinkedHashMap<>();
    String root = artifactId + "/";
    entries.put(root + "README.md", "# Forge E2E".getBytes(StandardCharsets.UTF_8));
    entries.put(root + "pom.xml", generatedPomXml(artifactId).getBytes(StandardCharsets.UTF_8));
    entries.put(
        root + "src/main/resources/application.properties",
        "quarkus.banner.enabled=false".getBytes(StandardCharsets.UTF_8));
    entries.put(
        root + "src/main/java/org/acme/GreetingResource.java",
        """
        package org.acme;

        import jakarta.ws.rs.GET;
        import jakarta.ws.rs.Path;
        import jakarta.ws.rs.Produces;
        import jakarta.ws.rs.core.MediaType;

        @Path("/hello")
        public class GreetingResource {
          @GET
          @Produces(MediaType.TEXT_PLAIN)
          public String hello() {
            return "hello";
          }
        }
        """
            .getBytes(StandardCharsets.UTF_8));

    Path zipPath = ArchiveTestUtils.createZip(tempDir.resolve("generated.zip"), entries);
    return Files.readAllBytes(zipPath);
  }

  private static String generatedPomXml(String artifactId) {
    return """
        <project xmlns="http://maven.apache.org/POM/4.0.0"
                 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                 xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
          <modelVersion>4.0.0</modelVersion>
          <groupId>org.acme</groupId>
          <artifactId>%s</artifactId>
          <version>1.0.0-SNAPSHOT</version>
          <properties>
            <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
            <maven.compiler.release>21</maven.compiler.release>
            <quarkus.platform.group-id>io.quarkus.platform</quarkus.platform.group-id>
            <quarkus.platform.artifact-id>quarkus-bom</quarkus.platform.artifact-id>
            <quarkus.platform.version>3.8.6</quarkus.platform.version>
          </properties>
          <dependencyManagement>
            <dependencies>
              <dependency>
                <groupId>${quarkus.platform.group-id}</groupId>
                <artifactId>${quarkus.platform.artifact-id}</artifactId>
                <version>${quarkus.platform.version}</version>
                <type>pom</type>
                <scope>import</scope>
              </dependency>
            </dependencies>
          </dependencyManagement>
          <dependencies>
            <dependency>
              <groupId>io.quarkus</groupId>
              <artifactId>quarkus-resteasy-reactive</artifactId>
            </dependency>
            <dependency>
              <groupId>io.quarkus</groupId>
              <artifactId>quarkus-arc</artifactId>
            </dependency>
          </dependencies>
          <build>
            <plugins>
              <plugin>
                <groupId>${quarkus.platform.group-id}</groupId>
                <artifactId>quarkus-maven-plugin</artifactId>
                <version>${quarkus.platform.version}</version>
                <extensions>true</extensions>
              </plugin>
              <plugin>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.14.1</version>
                <configuration>
                  <release>${maven.compiler.release}</release>
                </configuration>
              </plugin>
            </plugins>
          </build>
        </project>
        """
        .formatted(artifactId);
  }
}
