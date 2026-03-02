package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StartupOverlayTrackerTest {
  private StartupOverlayTracker tracker;

  @BeforeEach
  void setUp() {
    tracker = new StartupOverlayTracker();
  }

  @Test
  void notVisibleWhenCatalogNotLoadingAndNoDuration() {
    assertThat(tracker.isVisible(false)).isFalse();
  }

  @Test
  void visibleWhileCatalogLoading() {
    assertThat(tracker.isVisible(true)).isTrue();
  }

  @Test
  void activateOnFirstLoadMakesVisibleForDuration() {
    tracker.setMinDuration(Duration.ofSeconds(5));
    tracker.activateIfFirstLoad(1);
    assertThat(tracker.isVisible(false)).isTrue();
  }

  @Test
  void secondLoadDoesNotReactivate() {
    tracker.setMinDuration(Duration.ofSeconds(5));
    tracker.activateIfFirstLoad(2);
    assertThat(tracker.isVisible(false)).isFalse();
  }

  @Test
  void tickReturnsTrueWhenOverlayJustExpired() {
    // Initially visible due to catalog loading
    tracker.tick(true);
    // Catalog done, no duration → just expired
    boolean needsRepaint = tracker.tick(false);
    assertThat(needsRepaint).isTrue();
  }

  @Test
  void tickReturnsFalseWhenStillVisible() {
    boolean needsRepaint = tracker.tick(true);
    assertThat(needsRepaint).isFalse();
  }

  @Test
  void tickReturnsFalseWhenNeverVisible() {
    boolean needsRepaint = tracker.tick(false);
    assertThat(needsRepaint).isFalse();
  }

  @Test
  void zeroDurationIsHandled() {
    tracker.setMinDuration(Duration.ZERO);
    tracker.activateIfFirstLoad(1);
    assertThat(tracker.isVisible(false)).isFalse();
  }
}
