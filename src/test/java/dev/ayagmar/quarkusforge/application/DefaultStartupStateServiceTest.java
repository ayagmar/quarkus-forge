package dev.ayagmar.quarkusforge.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.api.MetadataDto;
import dev.ayagmar.quarkusforge.api.PlatformStream;
import dev.ayagmar.quarkusforge.domain.CliPrefill;
import dev.ayagmar.quarkusforge.domain.MetadataCompatibilityContext;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DefaultStartupStateServiceTest {
  private static final MetadataDto METADATA =
      new MetadataDto(
          List.of("21", "25"),
          List.of("maven", "gradle"),
          Map.of("maven", List.of("21", "25"), "gradle", List.of("21", "25")),
          List.of(
              new PlatformStream("io.quarkus.platform:3.31", "3.31", true, List.of("21", "25"))));

  private final DefaultStartupStateService service = new DefaultStartupStateService();

  @Test
  void resolveMergesRequestedStoredAndDefaultPrefill() {
    StartupMetadataSelection selection =
        new StartupMetadataSelection(MetadataCompatibilityContext.success(METADATA), "live", "");
    AtomicInteger loadCalls = new AtomicInteger();
    StartupRequest request =
        new StartupRequest(
            new CliPrefill("com.requested", "", "", "", ".", "", "", ""),
            new CliPrefill(
                "org.saved",
                "saved-app",
                "2.0.0",
                "org.saved.app",
                "saved-out",
                "io.quarkus.platform:3.20",
                "gradle",
                "21"),
            () -> {
              loadCalls.incrementAndGet();
              return selection;
            });

    StartupState startupState = service.resolve(request);

    assertThat(loadCalls).hasValue(1);
    assertThat(startupState.metadataSelection()).isSameAs(selection);
    assertThat(startupState.initialState().request().groupId()).isEqualTo("com.requested");
    assertThat(startupState.initialState().request().artifactId()).isEqualTo("saved-app");
    assertThat(startupState.initialState().request().version()).isEqualTo("2.0.0");
    assertThat(startupState.initialState().request().packageName()).isEqualTo("org.saved.app");
    assertThat(startupState.initialState().request().outputDirectory()).isEqualTo(".");
    assertThat(startupState.initialState().request().platformStream())
        .isEqualTo("io.quarkus.platform:3.20");
    assertThat(startupState.initialState().request().buildTool()).isEqualTo("gradle");
    assertThat(startupState.initialState().request().javaVersion()).isEqualTo("21");
  }

  @Test
  void resolveFallsBackToCompiledDefaultsWhenNoPrefillIsProvided() {
    StartupMetadataSelection selection =
        new StartupMetadataSelection(MetadataCompatibilityContext.success(METADATA), "live", "");
    StartupRequest request =
        new StartupRequest(
            new CliPrefill(null, null, null, null, null, null, null, null), null, () -> selection);

    StartupState startupState = service.resolve(request);

    assertThat(startupState.initialState().request().groupId()).isEqualTo("org.acme");
    assertThat(startupState.initialState().request().artifactId()).isEqualTo("quarkus-app");
    assertThat(startupState.initialState().request().version()).isEqualTo("1.0.0-SNAPSHOT");
    assertThat(startupState.initialState().request().packageName())
        .isEqualTo("org.acme.quarkus.app");
    assertThat(startupState.initialState().request().outputDirectory()).isEqualTo(".");
    assertThat(startupState.initialState().request().buildTool()).isEqualTo("maven");
    assertThat(startupState.initialState().request().javaVersion()).isEqualTo("25");
    assertThat(startupState.initialState().request().platformStream())
        .isEqualTo("io.quarkus.platform:3.31");
  }
}
