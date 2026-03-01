package dev.ayagmar.quarkusforge;

import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import dev.ayagmar.quarkusforge.ui.PostGenerationExitPlan;
import java.util.Objects;

record TuiSessionSummary(ProjectRequest finalRequest, PostGenerationExitPlan exitPlan) {
  TuiSessionSummary {
    Objects.requireNonNull(finalRequest);
  }
}
