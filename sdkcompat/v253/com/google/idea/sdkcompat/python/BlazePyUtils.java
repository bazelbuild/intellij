package com.google.idea.sdkcompat.python;

import com.intellij.execution.configurations.RunProfileState;
import java.util.function.Function;

// #api251
public class BlazePyUtils {

  public static RunProfileState getApiSpecificRunProfileState(
      RunProfileState profileState,
      Function<RunProfileState, RunProfileState> transformation) {
    return transformation.apply(profileState);
  }

}
