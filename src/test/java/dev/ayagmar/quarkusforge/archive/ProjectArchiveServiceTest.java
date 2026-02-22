package dev.ayagmar.quarkusforge.archive;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.ayagmar.quarkusforge.api.GenerationRequest;
import dev.ayagmar.quarkusforge.api.ObjectMapperProvider;
import dev.ayagmar.quarkusforge.api.QuarkusApiClient;
import dev.ayagmar.quarkusforge.api.RetryPolicy;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectArchiveServiceTest {
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
  void cancellationBeforeDownloadSkipsHttpCallAndTempArchiveCreation() throws Exception {
    stubFor(
        get(urlPathEqualTo("/api/download"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(createZipPayload("demo/pom.xml", "<project/>"))));

    Path tempArchive = tempDir.resolve("download.zip");
    ProjectArchiveService service =
        new ProjectArchiveService(newClient(), new SafeZipExtractor(), () -> tempArchive);

    GenerationRequest request =
        new GenerationRequest("com.example", "demo", "1.0.0", "maven", "25", List.of());

    assertThatThrownBy(
            () ->
                service
                    .downloadAndExtract(
                        request,
                        tempDir.resolve("generated-project"),
                        OverwritePolicy.FAIL_IF_EXISTS,
                        () -> true)
                    .join())
        .isInstanceOf(java.util.concurrent.CancellationException.class);

    verify(0, getRequestedFor(urlPathEqualTo("/api/download")));
    assertThat(Files.exists(tempArchive)).isFalse();
    assertThat(Files.exists(tempDir.resolve("generated-project"))).isFalse();
  }

  @Test
  void extractionFailureCleansTemporaryArchive() {
    stubFor(
        get(urlPathEqualTo("/api/download"))
            .willReturn(
                aResponse().withStatus(200).withBody("corrupt".getBytes(StandardCharsets.UTF_8))));

    Path tempArchive = tempDir.resolve("download.zip");
    ProjectArchiveService service =
        new ProjectArchiveService(newClient(), new SafeZipExtractor(), () -> tempArchive);

    GenerationRequest request =
        new GenerationRequest("com.example", "demo", "1.0.0", "maven", "25", List.of());

    assertThatThrownBy(
            () ->
                service
                    .downloadAndExtract(
                        request,
                        tempDir.resolve("generated-project"),
                        OverwritePolicy.FAIL_IF_EXISTS)
                    .join())
        .isInstanceOf(CompletionException.class)
        .hasRootCauseInstanceOf(ArchiveException.class);
    assertThat(Files.exists(tempArchive)).isFalse();
    assertThat(Files.exists(tempDir.resolve("generated-project"))).isFalse();
  }

  @Test
  void successfulExtractionDeletesTemporaryArchive() throws Exception {
    stubFor(
        get(urlPathEqualTo("/api/download"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(createZipPayload("demo/pom.xml", "<project/>"))));

    Path tempArchive = tempDir.resolve("download.zip");
    ProjectArchiveService service =
        new ProjectArchiveService(newClient(), new SafeZipExtractor(), () -> tempArchive);

    GenerationRequest request =
        new GenerationRequest("com.example", "demo", "1.0.0", "maven", "25", List.of());

    Path output = tempDir.resolve("generated-project");
    Path extracted =
        service.downloadAndExtract(request, output, OverwritePolicy.FAIL_IF_EXISTS).join();

    assertThat(extracted).isEqualTo(output);
    assertThat(Files.exists(output.resolve("pom.xml"))).isTrue();
    assertThat(Files.exists(tempArchive)).isFalse();
  }

  @Test
  void generatedZipPayloadIsNotPersistedAsCacheArtifact() throws Exception {
    stubFor(
        get(urlPathEqualTo("/api/download"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(createZipPayload("demo/pom.xml", "<project/>"))));

    Path tempArchiveDirectory = tempDir.resolve("archive-staging");
    Files.createDirectories(tempArchiveDirectory);
    Path tempArchive = tempArchiveDirectory.resolve("download.zip");
    ProjectArchiveService service =
        new ProjectArchiveService(newClient(), new SafeZipExtractor(), () -> tempArchive);

    GenerationRequest request =
        new GenerationRequest("com.example", "demo", "1.0.0", "maven", "25", List.of());

    Path output = tempDir.resolve("generated-project");
    service.downloadAndExtract(request, output, OverwritePolicy.FAIL_IF_EXISTS).join();

    assertThat(Files.exists(output.resolve("pom.xml"))).isTrue();
    assertThat(Files.exists(tempArchive)).isFalse();
    try (var stagedFiles = Files.list(tempArchiveDirectory)) {
      assertThat(stagedFiles.findAny()).isEmpty();
    }
  }

  @Test
  void extractionRunsOnConfiguredExecutor() throws Exception {
    stubFor(
        get(urlPathEqualTo("/api/download"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(createZipPayload("demo/pom.xml", "<project/>"))));

    Path tempArchive = tempDir.resolve("download.zip");
    ExecutorService extractionExecutor =
        Executors.newSingleThreadExecutor(
            runnable -> {
              Thread thread = new Thread(runnable, "archive-extractor-test");
              thread.setDaemon(true);
              return thread;
            });
    try {
      ProjectArchiveService service =
          new ProjectArchiveService(
              newClient(), new SafeZipExtractor(), () -> tempArchive, extractionExecutor);

      GenerationRequest request =
          new GenerationRequest("com.example", "demo", "1.0.0", "maven", "25", List.of());

      AtomicReference<String> extractionThreadName = new AtomicReference<>();
      Path output = tempDir.resolve("generated-project");
      service
          .downloadAndExtract(
              request,
              output,
              OverwritePolicy.FAIL_IF_EXISTS,
              () -> false,
              progress -> {
                if (progress == ProjectArchiveService.ProgressStep.EXTRACTING_ARCHIVE) {
                  extractionThreadName.set(Thread.currentThread().getName());
                }
              })
          .join();

      assertThat(extractionThreadName.get()).contains("archive-extractor-test");
      assertThat(Files.exists(output.resolve("pom.xml"))).isTrue();
      assertThat(Files.exists(tempArchive)).isFalse();
    } finally {
      extractionExecutor.shutdownNow();
    }
  }

  @Test
  void rejectedExtractionExecutorFailsFastAndCleansTemporaryArchive() throws Exception {
    stubFor(
        get(urlPathEqualTo("/api/download"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(createZipPayload("demo/pom.xml", "<project/>"))));

    Path tempArchive = tempDir.resolve("download.zip");
    Executor rejectingExecutor =
        command -> {
          throw new RejectedExecutionException("executor saturated");
        };
    ProjectArchiveService service =
        new ProjectArchiveService(
            newClient(), new SafeZipExtractor(), () -> tempArchive, rejectingExecutor);

    GenerationRequest request =
        new GenerationRequest("com.example", "demo", "1.0.0", "maven", "25", List.of());

    assertThatThrownBy(
            () ->
                service
                    .downloadAndExtract(
                        request,
                        tempDir.resolve("generated-project"),
                        OverwritePolicy.FAIL_IF_EXISTS)
                    .join())
        .isInstanceOf(CompletionException.class)
        .hasCauseInstanceOf(RejectedExecutionException.class);
    assertThat(Files.exists(tempArchive)).isFalse();
    assertThat(Files.exists(tempDir.resolve("generated-project"))).isFalse();
  }

  @Test
  void cancellationAfterExtractionStartsCompletesAsCancelled() throws Exception {
    stubFor(
        get(urlPathEqualTo("/api/download"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(createZipPayload("demo/pom.xml", "<project/>"))));

    Path tempArchive = tempDir.resolve("download.zip");
    ProjectArchiveService service =
        new ProjectArchiveService(newClient(), new SafeZipExtractor(), () -> tempArchive);

    GenerationRequest request =
        new GenerationRequest("com.example", "demo", "1.0.0", "maven", "25", List.of());

    AtomicBoolean cancelled = new AtomicBoolean(false);
    assertThatThrownBy(
            () ->
                service
                    .downloadAndExtract(
                        request,
                        tempDir.resolve("generated-project"),
                        OverwritePolicy.FAIL_IF_EXISTS,
                        cancelled::get,
                        progress -> {
                          if (progress == ProjectArchiveService.ProgressStep.EXTRACTING_ARCHIVE) {
                            cancelled.set(true);
                          }
                        })
                    .join())
        .isInstanceOf(CompletionException.class)
        .hasCauseInstanceOf(java.util.concurrent.CancellationException.class);
    assertThat(Files.exists(tempArchive)).isFalse();
  }

  private QuarkusApiClient newClient() {
    return new QuarkusApiClient(
        HttpClient.newHttpClient(),
        ObjectMapperProvider.shared(),
        URI.create(wireMockServer.baseUrl()),
        new RetryPolicy(1, Duration.ofSeconds(3), Duration.ofMillis(1), 0.0d),
        delay -> java.util.concurrent.CompletableFuture.completedFuture(null),
        Clock.fixed(Instant.parse("2026-02-21T00:00:00Z"), ZoneOffset.UTC),
        () -> 0.5d);
  }

  private static byte[] createZipPayload(String entryName, String content) throws Exception {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    try (ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream)) {
      zipOutputStream.putNextEntry(new ZipEntry(entryName));
      zipOutputStream.write(content.getBytes(StandardCharsets.UTF_8));
      zipOutputStream.closeEntry();
    }
    return byteArrayOutputStream.toByteArray();
  }
}
