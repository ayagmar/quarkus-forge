package dev.ayagmar.quarkusforge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ForgeCompatibilityBridgeTest {
  @TempDir Path tempDir;

  @Test
  void deprecatedForgefileLockBridgesToNewPackageType() {
    ForgefileLock compatibilityLock =
        ForgefileLock.of(
            "io.quarkus.platform:3.31", "maven", "25", List.of("web"), List.of("rest"));

    var newLock = compatibilityLock.toForgefileLock();

    assertThat(newLock.platformStream()).isEqualTo("io.quarkus.platform:3.31");
    assertThat(ForgefileLock.from(newLock)).isEqualTo(compatibilityLock);
  }

  @Test
  void deprecatedForgefileStoreDelegatesToNewStore() {
    Path forgefilePath = tempDir.resolve("Forgefile.json");
    Forgefile forgefile =
        new Forgefile(
            "com.example",
            "demo-app",
            "1.0.0",
            "com.example.demo",
            ".",
            "io.quarkus.platform:3.31",
            "maven",
            "25",
            List.of("web"),
            List.of("io.quarkus:quarkus-rest"),
            null);

    ForgefileStore.save(forgefilePath, forgefile);

    assertThat(ForgefileStore.load(forgefilePath)).isEqualTo(forgefile);
  }

  @Test
  void deprecatedForgefileBridgesToNewPackageType() {
    Forgefile compatibilityForgefile =
        new Forgefile(
            "com.example",
            "demo-app",
            "1.0.0",
            "com.example.demo",
            ".",
            "io.quarkus.platform:3.31",
            "maven",
            "25",
            List.of("web"),
            List.of("io.quarkus:quarkus-rest"),
            ForgefileLock.of(
                "io.quarkus.platform:3.31", "maven", "25", List.of("web"), List.of("rest")));

    var newForgefile = compatibilityForgefile.toForgefile();

    assertThat(newForgefile.groupId()).isEqualTo("com.example");
    assertThat(Forgefile.from(newForgefile)).isEqualTo(compatibilityForgefile);
  }
}
