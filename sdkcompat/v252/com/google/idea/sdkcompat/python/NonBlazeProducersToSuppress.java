package com.google.idea.sdkcompat.python;

public class NonBlazeProducersToSuppress {

  // PyTestsConfigurationProducer has internal visibility. We need to reference it to suppress it.
  @SuppressWarnings("KotlinInternal")
  public static final Class<?>[] PRODUCERS_TO_SUPPRESS = {
          com.jetbrains.python.testing.nosetestLegacy.PythonNoseTestConfigurationProducer.class,
          com.jetbrains.python.testing.pytestLegacy.PyTestConfigurationProducer.class,
          com.jetbrains.python.testing.unittestLegacy.PythonUnitTestConfigurationProducer.class};
}