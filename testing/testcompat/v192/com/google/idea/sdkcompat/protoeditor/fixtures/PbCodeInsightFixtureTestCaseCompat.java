/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.sdkcompat.protoeditor.fixtures;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.picocontainer.ComponentAdapter;
import org.picocontainer.MutablePicoContainer;

/**
 * CompatClass for stubbing out FeatureUsageTracker. Feature usage Tracker is a component in api192
 */
public class PbCodeInsightFixtureTestCaseCompat {
  private PbCodeInsightFixtureTestCaseCompat() {}

  public static void stubOutFeatureUsageTracker(Disposable parentDisposable) {
    MutablePicoContainer container =
        (MutablePicoContainer) ApplicationManager.getApplication().getPicoContainer();
    Class<?> keyClass = FeatureUsageTracker.class;
    String keyName = keyClass.getName();
    ComponentAdapter old = container.unregisterComponent(keyName);
    container.registerComponentInstance(keyName, new NoopFeatureUsageTracker());

    Disposer.register(
        parentDisposable,
        () -> {
          container.unregisterComponent(keyName);
          if (old != null) {
            container.registerComponent(old);
          }
        });
  }

  private static class NoopFeatureUsageTracker extends FeatureUsageTracker {

    @Override
    public void triggerFeatureUsed(String s) {}

    @Override
    public void triggerFeatureShown(String s) {}

    @Override
    public boolean isToBeShown(String s, Project project) {
      return false;
    }

    @Override
    public boolean isToBeAdvertisedInLookup(String s, Project project) {
      return false;
    }
  }
}
