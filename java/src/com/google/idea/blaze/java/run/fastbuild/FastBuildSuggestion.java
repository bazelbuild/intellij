/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.run.fastbuild;

import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.logging.EventLoggingService;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import javax.swing.event.HyperlinkEvent;

/**
 * Displays a notification suggesting to users that haven't tried fast builds that they should do
 * so.
 */
@State(name = "FastBuildSuggestion", storages = @Storage("fast.build.notification.xml"))
public final class FastBuildSuggestion
    implements PersistentStateComponent<FastBuildSuggestion.State> {

  private static final String URL = "https://ij.bazel.build/docs/fast-builds.html";

  // Don't notify unless it appears there will be at least this many jars on the test's classpath.
  // If there are only a handful of jars, there's no real advantage to a fast build.
  private static final int MIN_JARS_TO_NOTIFY = 500;

  private static final int MAX_TIMES_TO_DISPLAY = 5;
  private static final Duration MINIMUM_TIME_BETWEEN_DISPLAY = Duration.ofDays(2);

  private static final String NOTIFICATION_TITLE = "Try Fast Builds!";
  private static final NotificationGroup NOTIFICATION_GROUP =
      new NotificationGroup(NOTIFICATION_TITLE, NotificationDisplayType.STICKY_BALLOON, true);

  private State state = new State();

  public static FastBuildSuggestion getInstance() {
    return ApplicationManager.getApplication().getService(FastBuildSuggestion.class);
  }

  public void displayNotification(BlazeCommandRunConfiguration runProfile) {
    long msSinceLastDisplay = System.currentTimeMillis() - state.lastDisplayedTimeMs;

    if (state.triedFastBuild
        || state.timesDisplayed >= MAX_TIMES_TO_DISPLAY
        || msSinceLastDisplay < MINIMUM_TIME_BETWEEN_DISPLAY.toMillis()) {
      return;
    }

    if (!FastBuildConfigurationRunner.canRun(runProfile)
        || !isGoodCandidateForFastRun(runProfile)) {
      return;
    }

    logDisplay(msSinceLastDisplay);

    Notification notification =
        new Notification(
            NOTIFICATION_GROUP.getDisplayId(),
            NOTIFICATION_TITLE,
            "Tip: Try speeding up your Java tests by running them without Blaze using "
                + "<a href=\""
                + URL
                + "\">fast builds</a>.",
            NotificationType.INFORMATION,
            new OpenLinkAndLog());
    notification.notify(runProfile.getProject());
    state.lastDisplayedTimeMs = System.currentTimeMillis();
    state.timesDisplayed++;
  }

  private void logDisplay(long msSinceLastDisplay) {
    Map<String, String> data = new HashMap<>();
    data.put("timesDisplayed", Integer.toString(state.timesDisplayed + 1));
    if (state.lastDisplayedTimeMs > 0) {
      data.put("msSinceLastDisplay", Long.toString(msSinceLastDisplay));
    }
    EventLoggingService.getInstance().logEvent(FastBuildSuggestion.class, "notified", data);
  }

  private static boolean isGoodCandidateForFastRun(RunProfile runProfile) {
    if (!(runProfile instanceof BlazeCommandRunConfiguration)) {
      return false;
    }
    BlazeCommandRunConfiguration blazeCfg = (BlazeCommandRunConfiguration) runProfile;

    if (!(blazeCfg.getSingleTarget() instanceof Label)) {
      return false;
    }

    Project project = blazeCfg.getProject();
    if (Blaze.getProjectType(project).equals(ProjectType.QUERY_SYNC)) {
      // Fast Build is not supported with query sync
      return false;
    }
    Label label = (Label) blazeCfg.getSingleTarget();

    return countJavaDeps(label, blazeCfg.getProject()) > MIN_JARS_TO_NOTIFY;
  }

  private static int countJavaDeps(Label label, Project project) {
    TargetMap targetMap =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData().getTargetMap();
    AtomicInteger javaDeps = new AtomicInteger(0);
    countJavaDeps(TargetKey.forPlainTarget(label), targetMap, new HashSet<>(), javaDeps);
    return javaDeps.get();
  }

  private static void countJavaDeps(
      TargetKey input, TargetMap targetMap, Set<TargetKey> seenDeps, AtomicInteger javaDeps) {
    if (seenDeps.contains(input)) {
      return;
    }

    seenDeps.add(input);

    TargetIdeInfo targetIdeInfo = targetMap.get(input);
    if (targetIdeInfo != null && targetIdeInfo.getJavaIdeInfo() != null) {
      javaDeps.incrementAndGet();

      targetIdeInfo
          .getDependencies()
          .forEach(dep -> countJavaDeps(dep.getTargetKey(), targetMap, seenDeps, javaDeps));
    }
  }

  void triedFastBuild() {
    state.triedFastBuild = true;
  }

  @Override
  public void loadState(State state) {
    this.state = state;
  }

  @Nullable
  @Override
  public State getState() {
    return state;
  }

  /** State about the notifications. */
  public static class State {
    public long lastDisplayedTimeMs = 0;
    public int timesDisplayed = 0;
    public boolean triedFastBuild = false;
  }

  private static class OpenLinkAndLog implements NotificationListener {

    private final NotificationListener openLink =
        new UrlOpeningListener(/* expireNotification= */ true);

    @Override
    public void hyperlinkUpdate(Notification notification, HyperlinkEvent event) {
      EventLoggingService.getInstance().logEvent(FastBuildSuggestion.class, "clicked");
      openLink.hyperlinkUpdate(notification, event);
    }
  }
}
