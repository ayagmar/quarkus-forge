package dev.ayagmar.quarkusforge.runtime;

import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import dev.ayagmar.quarkusforge.ui.PostGenerationExitPlan;
import java.util.Objects;

public record TuiSessionSummary(ProjectRequest finalRequest, PostGenerationExitPlan exitPlan) {
  public TuiSessionSummary {
    Objects.requireNonNull(finalRequest);
  }
}
