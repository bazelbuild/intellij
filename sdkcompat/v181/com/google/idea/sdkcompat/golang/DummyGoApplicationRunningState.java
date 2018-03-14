package com.google.idea.sdkcompat.golang;

import com.goide.runconfig.application.GoApplicationConfiguration;
import com.goide.runconfig.application.GoApplicationRunningState;
import com.goide.util.GoExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.module.Module;
import java.util.List;
import javax.annotation.Nullable;

/** Adapter to bridge different SDK versions. */
public class DummyGoApplicationRunningState extends GoApplicationRunningState {

  public DummyGoApplicationRunningState(
      ExecutionEnvironment executionEnvironment,
      Module module,
      GoApplicationConfiguration goApplicationConfiguration) {
    super(executionEnvironment, module, goApplicationConfiguration);
  }

  @Nullable
  @Override
  public List<String> getBuildingTarget() {
    return null;
  }

  @Nullable
  @Override
  public GoExecutor createBuildExecutor() {
    return null;
  }
}
