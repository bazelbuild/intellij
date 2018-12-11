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
package com.google.idea.blaze.base.ideinfo;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.SyncListener;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;

/** Utility class to intern frequently duplicated objects in the project data. */
public abstract class ProjectDataInterner {
  private static final BoolExperiment internProjectData =
      new BoolExperiment("intern.project.data", true);

  private static ProjectDataInterner instance =
      ApplicationManager.getApplication() == null
              || ApplicationManager.getApplication().isUnitTestMode()
              || internProjectData.getValue()
          ? new Impl()
          : new NoOp();

  public static Label intern(Label label) {
    return instance.doIntern(label);
  }

  static String intern(String string) {
    return instance.doIntern(string);
  }

  static TargetKey intern(TargetKey targetKey) {
    return instance.doIntern(targetKey);
  }

  static Dependency intern(Dependency dependency) {
    return instance.doIntern(dependency);
  }

  static ArtifactLocation intern(ArtifactLocation artifactLocation) {
    return instance.doIntern(artifactLocation);
  }

  static AndroidResFolder intern(AndroidResFolder androidResFolder) {
    return instance.doIntern(androidResFolder);
  }

  public static ExecutionRootPath intern(ExecutionRootPath executionRootPath) {
    return instance.doIntern(executionRootPath);
  }

  abstract Label doIntern(Label label);

  abstract String doIntern(String string);

  abstract TargetKey doIntern(TargetKey targetKey);

  abstract Dependency doIntern(Dependency dependency);

  abstract ArtifactLocation doIntern(ArtifactLocation artifactLocation);

  abstract AndroidResFolder doIntern(AndroidResFolder androidResFolder);

  abstract ExecutionRootPath doIntern(ExecutionRootPath executionRootPath);

  private static class NoOp extends ProjectDataInterner {
    @Override
    Label doIntern(Label label) {
      return label;
    }

    @Override
    String doIntern(String string) {
      return string;
    }

    @Override
    TargetKey doIntern(TargetKey targetKey) {
      return targetKey;
    }

    @Override
    Dependency doIntern(Dependency dependency) {
      return dependency;
    }

    @Override
    ArtifactLocation doIntern(ArtifactLocation artifactLocation) {
      return artifactLocation;
    }

    @Override
    AndroidResFolder doIntern(AndroidResFolder androidResFolder) {
      return androidResFolder;
    }

    @Override
    ExecutionRootPath doIntern(ExecutionRootPath executionRootPath) {
      return executionRootPath;
    }
  }

  private static class Impl extends ProjectDataInterner {
    private static final Interner<Label> labelInterner = Interners.newWeakInterner();
    private static final Interner<String> stringInterner = Interners.newWeakInterner();
    private static final Interner<TargetKey> targetKeyInterner = Interners.newWeakInterner();
    private static final Interner<Dependency> dependencyInterner = Interners.newWeakInterner();
    private static final Interner<ArtifactLocation> artifactLocationInterner =
        Interners.newWeakInterner();
    private static final Interner<AndroidResFolder> androidResFolderInterner =
        Interners.newWeakInterner();
    private static final Interner<ExecutionRootPath> executionRootPathInterner =
        Interners.newWeakInterner();

    @Override
    Label doIntern(Label label) {
      return labelInterner.intern(label);
    }

    @Override
    String doIntern(String string) {
      return stringInterner.intern(string);
    }

    @Override
    TargetKey doIntern(TargetKey targetKey) {
      return targetKeyInterner.intern(targetKey);
    }

    @Override
    Dependency doIntern(Dependency dependency) {
      return dependencyInterner.intern(dependency);
    }

    @Override
    ArtifactLocation doIntern(ArtifactLocation artifactLocation) {
      return artifactLocationInterner.intern(artifactLocation);
    }

    @Override
    AndroidResFolder doIntern(AndroidResFolder androidResFolder) {
      return androidResFolderInterner.intern(androidResFolder);
    }

    @Override
    ExecutionRootPath doIntern(ExecutionRootPath executionRootPath) {
      return executionRootPathInterner.intern(executionRootPath);
    }
  }

  static class Updater implements SyncListener {
    @Override
    public void onSyncStart(Project project, BlazeContext context, SyncMode syncMode) {
      instance = internProjectData.getValue() ? new Impl() : new NoOp();
    }
  }
}
