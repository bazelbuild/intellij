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

import com.google.idea.blaze.base.logging.EventLoggingService;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import javax.swing.event.HyperlinkEvent;

/**
 * Displays a notification suggesting to users that haven't tried fast builds that they should do
 * so.
 */
@State(name = "FastBuildSuggestion", storages = @Storage("fast.build.notification.xml"))
public final class FastBuildSuggestion
    implements PersistentStateComponent<FastBuildSuggestion.State> {

  private static final int MAX_TIMES_TO_DISPLAY = 5;
  private static final Duration MINIMUM_TIME_BETWEEN_DISPLAY = Duration.ofDays(2);

  private static final String NOTIFICATION_TITLE = "Try Fast Builds!";
  private static final NotificationGroup NOTIFICATION_GROUP =
      new NotificationGroup(NOTIFICATION_TITLE, NotificationDisplayType.STICKY_BALLOON, true);

  private static final BoolExperiment enabled = new BoolExperiment("fast.build.suggestions", true);

  private State state = new State();

  public static FastBuildSuggestion getInstance() {
    return ServiceManager.getService(FastBuildSuggestion.class);
  }

  public void displayNotification(BlazeCommandRunConfiguration runProfile) {
    if (!FastBuildConfigurationRunner.canRun(runProfile)) {
      return;
    }

    long msSinceLastDisplay = System.currentTimeMillis() - state.lastDisplayedTimeMs;

    if (!enabled.getValue()
        || state.triedFastBuild
        || state.timesDisplayed >= MAX_TIMES_TO_DISPLAY
        || msSinceLastDisplay < MINIMUM_TIME_BETWEEN_DISPLAY.toMillis()) {
      return;
    }

    logDisplay(msSinceLastDisplay);

    Notification notification =
        new Notification(
            NOTIFICATION_GROUP.getDisplayId(),
            NOTIFICATION_TITLE,
            "Tip: Try speeding up your Java tests by running them without Blaze using "
                + "<a href=\"http://goto.google.com/intellij-fast-build\">fast builds</a>.",
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
    EventLoggingService.getInstance()
        .ifPresent(service -> service.logEvent(FastBuildSuggestion.class, "notified", data));
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
        new UrlOpeningListener(/* expireNotification */ true);

    @Override
    public void hyperlinkUpdate(Notification notification, HyperlinkEvent event) {
      EventLoggingService.getInstance()
          .ifPresent(service -> service.logEvent(FastBuildSuggestion.class, "clicked"));
      openLink.hyperlinkUpdate(notification, event);
    }
  }
}
