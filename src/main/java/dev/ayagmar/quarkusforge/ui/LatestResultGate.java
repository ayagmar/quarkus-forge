package dev.ayagmar.quarkusforge.ui;

final class LatestResultGate {
  private long latestToken = 0L;
  private boolean cancelled;

  synchronized long nextToken() {
    cancelled = false;
    latestToken++;
    return latestToken;
  }

  synchronized void cancel() {
    cancelled = true;
  }

  synchronized boolean shouldApply(long token) {
    return !cancelled && token == latestToken;
  }
}
