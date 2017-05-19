package com.google.idea.sdkcompat.python;

import com.google.common.collect.ImmutableList;
import java.util.Collection;

/** List of python configuration producers for a given plugin version. */
public class PyConfigurationProducersList {

  public static final Collection<Class<?>> PRODUCERS_TO_SUPPRESS =
      ImmutableList.of(
          com.jetbrains.python.run.PythonRunConfigurationProducer.class,
          com.jetbrains.python.testing.attest.PythonAtTestConfigurationProducer.class,
          com.jetbrains.python.testing.nosetest.PythonNoseTestConfigurationProducer.class,
          com.jetbrains.python.testing.doctest.PythonDocTestConfigurationProducer.class,
          com.jetbrains.python.testing.pytest.PyTestConfigurationProducer.class,
          com.jetbrains.python.testing.unittest.PythonUnitTestConfigurationProducer.class);
}
