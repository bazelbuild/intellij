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
package com.google.idea.blaze.golang.sync;

import com.google.idea.blaze.base.model.BlazeLibrary;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.LibraryKey;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library.ModifiableModel;
import javax.annotation.concurrent.Immutable;

/**
 * Dummy library for #api181
 *
 * <p>Remove once 181 is obsolete.
 */
@Immutable
public class BlazeGoLibrary extends BlazeLibrary {
  public static BoolExperiment useGoLibrary = new BoolExperiment("use.go.library", false);

  private static final long serialVersionUID = 1L;

  BlazeGoLibrary(BlazeProjectData projectData) {
    super(new LibraryKey("blaze_go_library"));
  }

  @Override
  public void modifyLibraryModel(
      Project project,
      ArtifactLocationDecoder artifactLocationDecoder,
      ModifiableModel libraryModel) {}
}
