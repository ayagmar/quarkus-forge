package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AsyncRepaintSignalTest {
  @Test
  void consumeReturnsTrueOnlyOncePerRequest() {
    AsyncRepaintSignal signal = new AsyncRepaintSignal();

    assertThat(signal.consume()).isFalse();

    signal.request();
    assertThat(signal.consume()).isTrue();
    assertThat(signal.consume()).isFalse();
  }
}
