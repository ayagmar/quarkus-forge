package dev.ayagmar.quarkusforge.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CliPrefillMapperTest {

  @Test
  void mapTrimsAllFields() {
    CliPrefill prefill =
        new CliPrefill("  com.example  ", "  demo  ", "  1.0  ", "  com.example.demo  ", "  ./out  ", "  maven  ", "  25  ");

    ProjectRequest request = CliPrefillMapper.map(prefill);

    assertThat(request.groupId()).isEqualTo("com.example");
    assertThat(request.artifactId()).isEqualTo("demo");
    assertThat(request.version()).isEqualTo("1.0");
    assertThat(request.packageName()).isEqualTo("com.example.demo");
    assertThat(request.outputDirectory()).isEqualTo("./out");
    assertThat(request.buildTool()).isEqualTo("maven");
    assertThat(request.javaVersion()).isEqualTo("25");
  }

  @Test
  void mapDerivesPackageNameWhenBlank() {
    CliPrefill prefill =
        new CliPrefill("com.example", "my-app", "1.0", "", "./out", "maven", "25");

    ProjectRequest request = CliPrefillMapper.map(prefill);

    assertThat(request.packageName()).isEqualTo("com.example.my.app");
  }

  @Test
  void mapDerivesPackageNameWhenNull() {
    CliPrefill prefill =
        new CliPrefill("com.example", "demo", "1.0", null, "./out", "maven", "25");

    ProjectRequest request = CliPrefillMapper.map(prefill);

    assertThat(request.packageName()).isEqualTo("com.example.demo");
  }

  @Test
  void mapPreservesExplicitPackageName() {
    CliPrefill prefill =
        new CliPrefill("com.example", "demo", "1.0", "org.custom.pkg", "./out", "maven", "25");

    ProjectRequest request = CliPrefillMapper.map(prefill);

    assertThat(request.packageName()).isEqualTo("org.custom.pkg");
  }

  @Test
  void derivePackageNamePrefixesDigitStartingSegment() {
    assertThat(CliPrefillMapper.derivePackageName("com.example", "123app"))
        .isEqualTo("com.example.x123app");
  }

  @Test
  void derivePackageNameStripsNonPackageChars() {
    assertThat(CliPrefillMapper.derivePackageName("com.example", "my@app!"))
        .isEqualTo("com.example.myapp");
  }

  @Test
  void derivePackageNameReplacesHyphensWithDots() {
    assertThat(CliPrefillMapper.derivePackageName("com.example", "my-cool-app"))
        .isEqualTo("com.example.my.cool.app");
  }

  @Test
  void derivePackageNameReturnsGroupIdOnlyWhenArtifactIsBlank() {
    assertThat(CliPrefillMapper.derivePackageName("com.example", ""))
        .isEqualTo("com.example");
  }

  @Test
  void derivePackageNameReturnsArtifactOnlyWhenGroupIdIsBlank() {
    assertThat(CliPrefillMapper.derivePackageName("", "demo"))
        .isEqualTo("demo");
  }

  @Test
  void derivePackageNameHandlesNullInputs() {
    assertThat(CliPrefillMapper.derivePackageName(null, null)).isEmpty();
  }

  @Test
  void derivePackageNameSkipsBlankSegments() {
    assertThat(CliPrefillMapper.derivePackageName("com.example", "a..b"))
        .isEqualTo("com.example.a.b");
  }

  @Test
  void derivePackageNameLowercases() {
    assertThat(CliPrefillMapper.derivePackageName("Com.EXAMPLE", "MyApp"))
        .isEqualTo("com.example.myapp");
  }

  @Test
  void mapHandlesNullFields() {
    CliPrefill prefill =
        new CliPrefill(null, null, null, null, null, null, null);

    ProjectRequest request = CliPrefillMapper.map(prefill);

    assertThat(request.groupId()).isEmpty();
    assertThat(request.artifactId()).isEmpty();
    assertThat(request.version()).isEmpty();
    assertThat(request.outputDirectory()).isEmpty();
    assertThat(request.buildTool()).isEmpty();
    assertThat(request.javaVersion()).isEmpty();
  }

  @Test
  void mapIncludesPlatformStream() {
    CliPrefill prefill =
        new CliPrefill("com.example", "demo", "1.0", "com.example.demo", "./out", "io.quarkus.platform:3.18", "maven", "25");

    ProjectRequest request = CliPrefillMapper.map(prefill);

    assertThat(request.platformStream()).isEqualTo("io.quarkus.platform:3.18");
  }
}
