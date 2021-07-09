/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.java.run.producers;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.diagnostic.PluginException;
import com.intellij.execution.RunConfigurationProducerService;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Suppresses certain non-Blaze configuration producers in Blaze projects. */
public class NonBlazeProducerSuppressor implements ProjectComponent {

  private static final String KOTLIN_PLUGIN_ID = "org.jetbrains.kotlin";
  private static final String ANDROID_PLUGIN_ID = "org.jetbrains.android";
  private static final String GRADLE_PLUGIN_ID = "org.jetbrains.plugins.gradle";

  private static final ImmutableList<String> KOTLIN_PRODUCERS =
      ImmutableList.of(
          "org.jetbrains.kotlin.idea.run.KotlinJUnitRunConfigurationProducer",
          "org.jetbrains.kotlin.idea.run.KotlinPatternConfigurationProducer",
          "org.jetbrains.kotlin.idea.run.KotlinRunConfigurationProducer",
          "org.jetbrains.kotlin.idea.run.KotlinTestClassGradleConfigurationProducer",
          "org.jetbrains.kotlin.idea.run.KotlinTestMethodGradleConfigurationProducer");

  private static final ImmutableList<String> ANDROID_PRODUCERS =
      ImmutableList.of(
          "com.android.tools.idea.run.AndroidConfigurationProducer",
          "com.android.tools.idea.testartifacts.instrumented.AndroidTestConfigurationProducer",
          "com.android.tools.idea.testartifacts.junit.TestClassAndroidConfigurationProducer",
          "com.android.tools.idea.testartifacts.junit.TestDirectoryAndroidConfigurationProducer",
          "com.android.tools.idea.testartifacts.junit.TestMethodAndroidConfigurationProducer",
          "com.android.tools.idea.testartifacts.junit.TestPackageAndroidConfigurationProducer",
          "com.android.tools.idea.testartifacts.junit.TestPatternConfigurationProducer");

  private static final ImmutableList<String> GRADLE_PRODUCERS =
      ImmutableList.of(
          "org.jetbrains.plugins.gradle.execution.GradleGroovyScriptRunConfigurationProducer",
          "org.jetbrains.plugins.gradle.execution.test.runner.AllInDirectoryGradleConfigurationProducer",
          "org.jetbrains.plugins.gradle.execution.test.runner.AllInPackageGradleConfigurationProducer",
          "org.jetbrains.plugins.gradle.execution.test.runner.PatternGradleConfigurationProducer",
          "org.jetbrains.plugins.gradle.execution.test.runner.TestClassGradleConfigurationProducer",
          "org.jetbrains.plugins.gradle.execution.test.runner.TestMethodGradleConfigurationProducer",
          "org.jetbrains.plugins.gradle.service.execution.GradleRuntimeConfigurationProducer");

  private static final ImmutableList<Class<? extends RunConfigurationProducer<?>>> JAVA_PRODUCERS =
      ImmutableList.of(
          com.intellij.execution.junit.AbstractAllInDirectoryConfigurationProducer.class,
          com.intellij.execution.junit.AllInDirectoryConfigurationProducer.class,
          com.intellij.execution.junit.AllInPackageConfigurationProducer.class,
          com.intellij.execution.junit.TestInClassConfigurationProducer.class,
          com.intellij.execution.junit.TestClassConfigurationProducer.class,
          com.intellij.execution.junit.PatternConfigurationProducer.class,
          com.intellij.execution.junit.UniqueIdConfigurationProducer.class,
          com.intellij.execution.junit.testDiscovery.JUnitTestDiscoveryConfigurationProducer.class,
          com.intellij.execution.application.ApplicationConfigurationProducer.class);

  // #api211 TestMethodConfigurationProducer is removed in 2021.2 so after 2021.1 is no longer
  // supported we can remove this function and directly add the list of JAVA_PRODUCERS to the
  // suppressed producers.
  private static ImmutableList<Class<? extends RunConfigurationProducer<?>>> getJavaProducers() {
    ClassLoader classLoader = JAVA_PRODUCERS.get(0).getClassLoader();
    Class<? extends RunConfigurationProducer<?>> clazz =
        loadClass(classLoader, "com.intellij.execution.junit.TestMethodConfigurationProducer");
    if (clazz != null) {
      return ImmutableList.<Class<? extends RunConfigurationProducer<?>>>builder()
          .addAll(JAVA_PRODUCERS)
          .add(clazz)
          .build();
    }
    return JAVA_PRODUCERS;
  }

  private static Collection<Class<? extends RunConfigurationProducer<?>>> getProducers(
      String pluginId, Collection<String> qualifiedClassNames) {
    // rather than compiling against additional plugins, and including a switch in the our
    // plugin.xml, just get the classes manually via the plugin class loader.
    IdeaPluginDescriptor plugin = PluginManager.getPlugin(PluginId.getId(pluginId));
    if (plugin == null || !plugin.isEnabled()) {
      return ImmutableList.of();
    }
    ClassLoader loader = plugin.getPluginClassLoader();
    return qualifiedClassNames.stream()
        .map((qualifiedName) -> loadClass(loader, qualifiedName))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  @SuppressWarnings("unchecked") // Instanceof check is right before cast.
  @Nullable
  private static Class<? extends RunConfigurationProducer<?>> loadClass(
      ClassLoader loader, String qualifiedName) {
    try {
      Class<?> clazz = loader.loadClass(qualifiedName);
      if (RunConfigurationProducer.class.isAssignableFrom(clazz)) {
        return (Class<? extends RunConfigurationProducer<?>>) clazz;
      }
      return null;
    } catch (PluginException | ClassNotFoundException | NoClassDefFoundError ignored) {
      return null;
    }
  }

  private final Project project;

  public NonBlazeProducerSuppressor(Project project) {
    this.project = project;
  }

  @Override
  public void projectOpened() {
    if (Blaze.isBlazeProject(project)) {
      suppressProducers(project);
    }
  }

  private static void suppressProducers(Project project) {
    RunConfigurationProducerService producerService =
        RunConfigurationProducerService.getInstance(project);
    ImmutableList.<Class<? extends RunConfigurationProducer<?>>>builder()
        .addAll(getJavaProducers())
        .addAll(getProducers(KOTLIN_PLUGIN_ID, KOTLIN_PRODUCERS))
        .addAll(getProducers(ANDROID_PLUGIN_ID, ANDROID_PRODUCERS))
        .addAll(getProducers(GRADLE_PLUGIN_ID, GRADLE_PRODUCERS))
        .build()
        .forEach(producerService::addIgnoredProducer);
  }
}
