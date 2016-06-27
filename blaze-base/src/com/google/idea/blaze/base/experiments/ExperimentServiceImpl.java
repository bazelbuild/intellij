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
package com.google.idea.blaze.base.experiments;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.SystemProperties;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An experiment service that delegates to {@link ExperimentLoader ExperimentLoaders}, in a
 * specific order.
 *
 * It will check system properties first, then an experiment file in the user's home directory, then
 * finally all files specified by the system property blaze.experiments.file.
 */
public class ExperimentServiceImpl implements ExperimentService {
  private static final Logger LOG = Logger.getInstance(ExperimentServiceImpl.class);

  private static final String USER_EXPERIMENT_OVERRIDES_FILE =
      SystemProperties.getUserHome() + File.separator + ".blaze-experiments";

  private final List<ExperimentLoader> services;
  private Map<String, String> experiments;
  private AtomicInteger experimentScopeCounter = new AtomicInteger(0);

  public ExperimentServiceImpl() {
    this(
        new SystemPropertyExperimentLoader(),
        new FileExperimentLoader(USER_EXPERIMENT_OVERRIDES_FILE),
        new WebExperimentLoader());
  }

  @VisibleForTesting
  protected ExperimentServiceImpl(ExperimentLoader... loaders) {
    services = ImmutableList.copyOf(loaders);
  }

  @Override
  public synchronized void reloadExperiments() {
    if (experimentScopeCounter.get() > 0) {
      return;
    }

    Map<String, String> experiments = Maps.newHashMap();
    for (ExperimentLoader loader : Lists.reverse(services)) {
      experiments.putAll(loader.getExperiments());
    }
    this.experiments = experiments;
  }

  @Override
  public synchronized void startExperimentScope() {
    this.experimentScopeCounter.incrementAndGet();
  }

  @Override
  public synchronized void endExperimentScope() {
    int value = this.experimentScopeCounter.decrementAndGet();
    LOG.assertTrue(value >= 0);
  }

  @Override
  public boolean getExperiment(@NotNull String key, boolean defaultValue) {
    String property = getExperiment(key);
    return property != null ? property.equals("1") : defaultValue;
  }

  @Override
  public String getExperimentString(@NotNull String key, String defaultValue) {
    String property = getExperiment(key);
    return property != null ? property : defaultValue;
  }

  @Override
  public int getExperimentInt(@NotNull String key, int defaultValue) {
    String property = getExperiment(key);
    try {
      return property != null ? Integer.parseInt(property) : defaultValue;
    } catch (NumberFormatException e) {
      LOG.warn("Could not parse int for experiment: " + key, e);
      return defaultValue;
    }
  }

  String getExperiment(@NotNull String key) {
    if (experiments == null) {
      reloadExperiments();
    }
    LOG.assertTrue(experiments != null, "Failure to load experiments.");
    return experiments.get(key);
  }
}
