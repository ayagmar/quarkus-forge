package dev.ayagmar.quarkusforge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QuarkusForgeCliTest {
  @Test
  void helpCommandReturnsSuccessExitCode() {
    int exitCode = QuarkusForgeCli.runWithArgs(new String[] {"--help"});
    assertThat(exitCode).isZero();
  }
}
