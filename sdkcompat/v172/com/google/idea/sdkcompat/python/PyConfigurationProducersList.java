package com.google.idea.sdkcompat.python;

import com.google.common.collect.ImmutableList;

/** List of python configuration producers for a given plugin version. */
public class PyConfigurationProducersList {

  public static final ImmutableList<Class<?>> PRODUCERS_TO_SUPPRESS =
      ImmutableList.of(
          com.jetbrains.python.run.PythonRunConfigurationProducer.class,
          com.jetbrains.python.testing.PyTestsConfigurationProducer.class,
          com.jetbrains.python.testing.PythonTestLegacyConfigurationProducer.class,
          com.jetbrains.python.testing.doctest.PythonDocTestConfigurationProducer.class,
          com.jetbrains.python.testing.nosetestLegacy.PythonNoseTestConfigurationProducer.class,
          com.jetbrains.python.testing.pytestLegacy.PyTestConfigurationProducer.class,
          com.jetbrains.python.testing.tox.PyToxConfigurationProducer.class,
          com.jetbrains.python.testing.unittestLegacy.PythonUnitTestConfigurationProducer.class);
}
