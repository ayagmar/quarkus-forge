package dev.ayagmar.quarkusforge.ui;

import java.time.Duration;

/**
 * Tracks startup overlay visibility. The overlay is visible while the catalog is loading or until a
 * minimum display duration elapses.
 */
final class StartupOverlayTracker {
  private long minDurationNanos;
  private long visibleUntilNanos;
  private boolean wasVisibleOnLastTick;

  boolean isVisible(boolean catalogLoading) {
    return catalogLoading || System.nanoTime() < visibleUntilNanos;
  }

  void setMinDuration(Duration duration) {
    minDurationNanos = Math.max(0L, duration.toNanos());
  }

  void activateIfFirstLoad(long loadToken) {
    if (loadToken == 1 && minDurationNanos > 0L) {
      visibleUntilNanos = System.nanoTime() + minDurationNanos;
    }
  }

  /**
   * Called on each tick. Returns {@code true} if a repaint is needed because the overlay just
   * expired (transitioned from visible to not visible).
   */
  boolean tick(boolean catalogLoading) {
    boolean visibleNow = isVisible(catalogLoading);
    boolean justExpired = wasVisibleOnLastTick && !visibleNow;
    wasVisibleOnLastTick = visibleNow;
    return justExpired;
  }
}
