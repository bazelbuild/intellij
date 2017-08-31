package com.google.idea.sdkcompat.java;

import com.google.common.collect.ImmutableList;
import com.intellij.execution.actions.RunConfigurationProducer;

/** List of java-related configuration producers for a given plugin version. */
public class JavaConfigurationProducerList {

  /**
   * Returns a list of run configuration producers to suppress for Blaze projects.
   *
   * <p>These classes must all be accessible from the Blaze plugin's classpath (e.g. they shouldn't
   * belong to any plugins not listed as dependencies of the Blaze plugin).
   */
  public static final ImmutableList<Class<? extends RunConfigurationProducer<?>>>
      PRODUCERS_TO_SUPPRESS =
          ImmutableList.of(
              com.intellij.execution.junit.AllInDirectoryConfigurationProducer.class,
              com.intellij.execution.junit.AllInPackageConfigurationProducer.class,
              com.intellij.execution.junit.TestClassConfigurationProducer.class,
              com.intellij.execution.junit.TestMethodConfigurationProducer.class,
              com.intellij.execution.junit.PatternConfigurationProducer.class,
              com.intellij.execution.application.ApplicationConfigurationProducer.class);
}
