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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableScheduledFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gson.JsonParseException;
import com.google.idea.blaze.base.util.SerializationUtil;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.io.HttpRequests;
import org.jetbrains.io.JsonReaderEx;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

/**
 * A singleton class that retrieves the experiments from the experiments service</a>.
 *
 * The first time {@link #getExperimentValues()} is called, fresh data will be retrieved in the
 * current thread. Thereafter, data will be retrieved every 5 minutes in a background thread. If
 * there is a failure retrieving data, new attempts will be made every minute.
 */
class WebExperimentSyncer {
  private static final String DEFAULT_EXPERIMENT_URL =
      "https://intellij-experiments.appspot.com/api/experiments";
  private static final String EXPERIMENTS_URL_PROPERTY = "blaze.experiments.url";

  private static final int SUCESSFUL_DOWNLOAD_DELAY_MINUTES = 5;
  private static final int DOWNLOAD_FAILURE_DELAY_MINUTES = 1;

  private static final String CACHE_FILE_NAME = "blaze.experiments.cache.dat";

  private static final Logger LOG = Logger.getInstance(WebExperimentSyncer.class);

  private static final WebExperimentSyncer INSTANCE = new WebExperimentSyncer();

  // null indicates no fetch attempt has been made. After the first attempt, this will always be a
  // (possibly empty) map.
  private Map<String, String> experimentValues = null;

  private ListeningScheduledExecutorService executor =
      MoreExecutors.listeningDecorator(
          MoreExecutors.getExitingScheduledExecutorService(
              new ScheduledThreadPoolExecutor(1), 0, TimeUnit.SECONDS));

  private WebExperimentSyncer() {}

  public static WebExperimentSyncer getInstance() {
    return INSTANCE;
  }

  /**
   * Get the last-retrieved set of experiment values.
   *
   * The first time this method is called, an attempt to retrieve the values will take place.
   * Thereafter, the values will be retrieved every five minutes on a background thread and this
   * method will return the most recent successfully retrieved values.
   */
  public synchronized Map<String, String> getExperimentValues() {
    if (experimentValues == null) {
      initialize();
    }

    return experimentValues;
  }

  private synchronized void setExperimentValues(HashMap<String, String> experimentValues) {
    this.experimentValues = experimentValues;
    saveCache(experimentValues);
  }

  /**
   * Fetch and process the experiments on the current thread.
   */
  private void initialize() {
    ListenableFuture<String> response =
        MoreExecutors.sameThreadExecutor().submit(new WebExperimentsDownloader());
    response.addListener(
        new WebExperimentsResultProcessor(response, false), MoreExecutors.sameThreadExecutor());

    // Failed to fetch, try to load cache from disk
    if (experimentValues == null) {
      experimentValues = loadCache();
    }

    // There must have been an error retrieving the experiments.
    if (experimentValues == null) {
      experimentValues = ImmutableMap.of();
    }
  }

  private void scheduleNextRefresh(boolean refreshWasSuccessful) {
    int delayInMinutes =
        refreshWasSuccessful ? SUCESSFUL_DOWNLOAD_DELAY_MINUTES : DOWNLOAD_FAILURE_DELAY_MINUTES;
    ListenableScheduledFuture<String> refreshResults =
        executor.schedule(new WebExperimentsDownloader(), delayInMinutes, TimeUnit.MINUTES);
    refreshResults.addListener(
        new WebExperimentsResultProcessor(refreshResults, true), MoreExecutors.sameThreadExecutor());
  }

  private static class WebExperimentsDownloader implements Callable<String> {

    @Override
    public String call() throws Exception {
      LOG.debug("About to fetch experiments.");
      return HttpRequests.request(
              System.getProperty(EXPERIMENTS_URL_PROPERTY, DEFAULT_EXPERIMENT_URL))
          .readString(null /* progress indicator */);
    }
  }

  private class WebExperimentsResultProcessor implements Runnable {

    private final Future<String> resultFuture;
    private final boolean triggerExperimentsReload;

    private WebExperimentsResultProcessor(Future<String> resultFuture,
                                          boolean triggerExperimentsReload) {
      this.resultFuture = resultFuture;
      this.triggerExperimentsReload = triggerExperimentsReload;
    }

    @Override
    public void run() {
      LOG.debug("Experiments fetched. Processing results.");
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

        if (triggerExperimentsReload) {
          ExperimentService.getInstance().reloadExperiments();
        }
        LOG.debug("Successfully fetched experiments: " + getExperimentValues());
        scheduleNextRefresh(true /* refreshWasSuccessful */);
      } catch (InterruptedException | ExecutionException | JsonParseException e) {
        LOG.debug("Error fetching experiments", e);
        scheduleNextRefresh(false /* refreshWasSuccessful */);
      }
    }
  }

  private static void saveCache(HashMap<String, String> experiments) {
    try {
      SerializationUtil.saveToDisk(getCacheFile(), experiments);
    } catch (IOException e) {
      LOG.warn("Could not save experiments cache to disk: " + getCacheFile());
    }
  }

  @SuppressWarnings("unchecked")
  private static HashMap<String, String> loadCache() {
    try {
      return (HashMap<String, String>)SerializationUtil.loadFromDisk(getCacheFile(), ImmutableList.of());
    }
    catch (IOException e) {
      // This is normal, we might be offline and have never loaded the cache.
      LOG.info("Could not load experiments file: " + getCacheFile());
    }
    return null;
  }

  private static File getCacheFile() {
    return new File(new File(PathManager.getSystemPath(), "blaze"), CACHE_FILE_NAME).getAbsoluteFile();
  }
}
