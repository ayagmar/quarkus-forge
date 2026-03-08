package dev.ayagmar.quarkusforge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ayagmar.quarkusforge.domain.CliPrefill;
import org.junit.jupiter.api.Test;

class StartupRequestTest {

  @Test
  void preservesRequestedAndStoredPrefillAlongsideMetadataLoader() {
    CliPrefill requestedPrefill =
        new CliPrefill("com.example", "demo", "1.0.0", "com.example.demo", ".", "", "maven", "21");
    CliPrefill storedPrefill =
        new CliPrefill("org.acme", "saved-app", "1.0.0-SNAPSHOT", null, ".", "", "maven", "25");
    StartupMetadataLoader metadataLoader = () -> null;

    StartupRequest request = new StartupRequest(requestedPrefill, storedPrefill, metadataLoader);

    assertThat(request.requestedPrefill()).isSameAs(requestedPrefill);
    assertThat(request.storedPrefill()).isSameAs(storedPrefill);
    assertThat(request.metadataLoader()).isSameAs(metadataLoader);
  }

  @Test
  void rejectsNullRequestedPrefill() {
    assertThatThrownBy(() -> new StartupRequest(null, null, () -> null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void rejectsNullMetadataLoader() {
    CliPrefill requestedPrefill =
        new CliPrefill("com.example", "demo", "1.0.0", "com.example.demo", ".", "", "maven", "21");

    assertThatThrownBy(() -> new StartupRequest(requestedPrefill, null, null))
        .isInstanceOf(NullPointerException.class);
  }
}
