package com.google.idea.sdkcompat.clion;

import com.google.common.collect.ImmutableList;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.jetbrains.cidr.cpp.execution.testing.CMakeGoogleTestRunConfigurationProducer;

/** List of C/C++ configuration producers for a given plugin version. */
public class CMakeConfigurationProducersList {
  public static final ImmutableList<Class<? extends RunConfigurationProducer<?>>>
      PRODUCERS_TO_SUPPRESS = ImmutableList.of(CMakeGoogleTestRunConfigurationProducer.class);
}
