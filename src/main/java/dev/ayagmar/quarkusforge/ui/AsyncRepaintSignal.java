package dev.ayagmar.quarkusforge.ui;

import java.util.concurrent.atomic.AtomicBoolean;

final class AsyncRepaintSignal {
  private final AtomicBoolean pending = new AtomicBoolean(false);

  void request() {
    pending.set(true);
  }

  boolean consume() {
    return pending.getAndSet(false);
  }
}
