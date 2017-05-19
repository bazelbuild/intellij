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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableScheduledFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.io.HttpRequests;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.jetbrains.io.JsonReaderEx;

/**
 * A singleton class that retrieves the experiments from the experiments service</a>.
 *
 * <p>The first time {@link #getExperimentValues()} is called, fresh data will be retrieved in the
 * current thread. Thereafter, data will be retrieved every 5 minutes in a background thread. If
 * there is a failure retrieving data, new attempts will be made every minute.
 */
final class WebExperimentSyncer {

  private static final Logger logger = Logger.getInstance(WebExperimentSyncer.class);

  private static final String DEFAULT_EXPERIMENT_URL =
      "https://intellij-experiments.appspot.com/api/experiments/";
  private static final String EXPERIMENTS_URL_PROPERTY = "intellij.experiments.url";

  private static final int SUCESSFUL_DOWNLOAD_DELAY_MINUTES = 5;
  private static final int DOWNLOAD_FAILURE_DELAY_MINUTES = 1;

  private final File cacheFile;
  private final String pluginName;

  // null indicates no fetch attempt has been made. After the first attempt, this will always be a
  // (possibly empty) map.
  private Map<String, String> experimentValues = null;

  private final ListeningScheduledExecutorService executor =
      MoreExecutors.listeningDecorator(AppExecutorUtil.getAppScheduledExecutorService());

  WebExperimentSyncer(String pluginName) {
    this.pluginName = pluginName;
    cacheFile =
        Paths.get(PathManager.getSystemPath(), "blaze", pluginName + ".experiments.cache.dat")
            .toFile();
  }

  /**
   * Get the last-retrieved set of experiment values.
   *
   * <p>The first time this method is called, an attempt to retrieve the values will take place.
   * Thereafter, the values will be retrieved every five minutes on a background thread and this
   * method will return the most recent successfully retrieved values.
   */
  synchronized Map<String, String> getExperimentValues() {
    if (experimentValues == null) {
      initialize();
    }

    return experimentValues;
  }

  private synchronized void setExperimentValues(HashMap<String, String> experimentValues) {
    this.experimentValues = experimentValues;
    saveCache(experimentValues);
  }

  /** Fetch and process the experiments on the current thread. */
  void initialize() {
    experimentValues = loadCache();
    ListenableFuture<String> response = executor.submit(new WebExperimentsDownloader());
    response.addListener(
        new WebExperimentsResultProcessor(response), MoreExecutors.directExecutor());
  }

  private void scheduleNextRefresh(boolean refreshWasSuccessful) {
    int delayInMinutes =
        refreshWasSuccessful ? SUCESSFUL_DOWNLOAD_DELAY_MINUTES : DOWNLOAD_FAILURE_DELAY_MINUTES;
    ListenableScheduledFuture<String> refreshResults =
        executor.schedule(new WebExperimentsDownloader(), delayInMinutes, TimeUnit.MINUTES);
    refreshResults.addListener(
        new WebExperimentsResultProcessor(refreshResults), MoreExecutors.directExecutor());
  }

  private class WebExperimentsDownloader implements Callable<String> {

    @Override
    public String call() throws Exception {
      logger.debug("About to fetch experiments.");
      return HttpRequests.request(
              System.getProperty(EXPERIMENTS_URL_PROPERTY, DEFAULT_EXPERIMENT_URL) + pluginName)
          .readString(/* progress indicator */ null);
    }
  }

  private class WebExperimentsResultProcessor implements Runnable {

    private final Future<String> resultFuture;

    private WebExperimentsResultProcessor(Future<String> resultFuture) {
      this.resultFuture = resultFuture;
    }

    @Override
    public void run() {
      logger.debug("Experiments fetched. Processing results.");
      try {
        HashMap<String, String> mapBuilder = Maps.newHashMap();
        String result = resultFuture.get();
        try (JsonReaderEx reader = new JsonReaderEx(result)) {
          reader.beginObject();
          while (reader.hasNext()) {
            String experimentName = reader.nextName();
            String experimentValue = reader.nextString();
            mapBuilder.put(experimentName, experimentValue);
          }
        }
        setExperimentValues(mapBuilder);

        logger.debug("Successfully fetched experiments: " + getExperimentValues());
        scheduleNextRefresh(/* refreshWasSuccessful */ true);
      } catch (InterruptedException | ExecutionException | RuntimeException e) {
        logger.debug("Error fetching experiments", e);
        scheduleNextRefresh(/* refreshWasSuccessful */ false);
      }
    }
  }

  private void saveCache(HashMap<String, String> experiments) {
    try {
      SerializationUtil.saveToDisk(cacheFile, experiments);
    } catch (IOException e) {
      logger.warn("Could not save experiments cache to disk: " + cacheFile);
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, String> loadCache() {
    try {
      Map<String, String> loaded = (Map<String, String>) SerializationUtil.loadFromDisk(cacheFile);
      return loaded != null ? loaded : ImmutableMap.of();
    } catch (IOException e) {
      // This is normal, we might be offline and have never loaded the cache.
      logger.info("Could not load experiments file: " + cacheFile);
    }
    return ImmutableMap.of();
  }
}
