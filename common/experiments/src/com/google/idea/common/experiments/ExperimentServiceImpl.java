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
package com.google.idea.common.experiments;

import static com.google.idea.common.experiments.ExperimentsUtil.hashExperimentName;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.Alarm;
import com.intellij.util.Alarm.ThreadToUse;
import com.intellij.util.SystemProperties;
import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * An experiment service that delegates to {@link ExperimentLoader ExperimentLoaders}, in a specific
 * order.
 *
 * <p>It will check system properties first, then an experiment file in the user's home directory,
 * then finally all files specified by the system property blaze.experiments.file.
 */
public class ExperimentServiceImpl implements ApplicationComponent, ExperimentService {
  private static final Logger logger = Logger.getInstance(ExperimentServiceImpl.class);

  private static final String USER_EXPERIMENT_OVERRIDES_FILE =
      SystemProperties.getUserHome() + File.separator + ".intellij-experiments";

  private static final Duration REFRESH_FREQUENCY = Duration.ofMinutes(5);

  private final Alarm alarm =
      new Alarm(ThreadToUse.POOLED_THREAD, ApplicationManager.getApplication());
  private final List<ExperimentLoader> services;
  private final AtomicInteger experimentScopeCounter = new AtomicInteger(0);

  private volatile Map<String, String> experiments = ImmutableMap.of();

  public ExperimentServiceImpl(String pluginName) {
    this(
        new SystemPropertyExperimentLoader(),
        new FileExperimentLoader(USER_EXPERIMENT_OVERRIDES_FILE),
        new WebExperimentLoader(pluginName));
  }

  @VisibleForTesting
  ExperimentServiceImpl(ExperimentLoader... loaders) {
    services = ImmutableList.copyOf(loaders);
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      refreshExperiments();
    } else {
      scheduleRefresh(Duration.ZERO);
    }
  }

  @Override
  public void initComponent() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      services.forEach(ExperimentLoader::initialize);
    }
  }

  @Override
  public boolean getExperiment(String key, boolean defaultValue) {
    String property = getExperiment(key);
    return property != null ? property.equals("1") : defaultValue;
  }

  @Override
  public String getExperimentString(String key, @Nullable String defaultValue) {
    String property = getExperiment(key);
    return property != null ? property : defaultValue;
  }

  @Override
  public int getExperimentInt(String key, int defaultValue) {
    String property = getExperiment(key);
    try {
      return property != null ? Integer.parseInt(property) : defaultValue;
    } catch (NumberFormatException e) {
      logger.warn("Could not parse int for experiment: " + key, e);
      return defaultValue;
    }
  }

  @Override
  public void startExperimentScope() {
    if (experimentScopeCounter.getAndIncrement() == 0) {
      // synchronously update experiments and keep them fixed for the duration of the scope
      refreshExperiments();
    }
  }

  @Override
  public void endExperimentScope() {
    int counter = experimentScopeCounter.decrementAndGet();
    logger.assertTrue(counter >= 0);
    if (counter <= 0 && ApplicationManager.getApplication().isUnitTestMode()) {
      refreshExperiments();
    }
  }

  private void scheduleRefresh(Duration delay) {
    if (alarm.isDisposed()) {
      return;
    }
    alarm.addRequest(
        () -> {
          try {
            if (experimentScopeCounter.get() <= 0) {
              refreshExperiments();
            }
          } finally {
            scheduleRefresh(REFRESH_FREQUENCY);
          }
        },
        delay.toMillis());
  }

  private void refreshExperiments() {
    experiments =
        services.stream()
            .flatMap(service -> service.getExperiments().entrySet().stream())
            .collect(
                Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, second) -> first));
  }

  private String getExperiment(String key) {
    return experiments.get(hashExperimentName(key));
  }
}
