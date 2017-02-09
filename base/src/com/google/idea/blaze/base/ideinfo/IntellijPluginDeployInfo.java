/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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

import com.google.common.collect.ImmutableList;
import java.io.Serializable;
import javax.annotation.concurrent.Immutable;

/** A special rule representing the files that need to be deployed for an IntelliJ plugin */
@Immutable
public class IntellijPluginDeployInfo implements Serializable {
  private static final long serialVersionUID = 1L;

  /** A single file for deployment */
  @Immutable
  public static class IntellijPluginDeployFile implements Serializable {
    private static final long serialVersionUID = 1L;

    /** The source file to deploy. */
    public final ArtifactLocation src;
    /** A plugins-directory relative location to deploy to. */
    public final String deployLocation;

    public IntellijPluginDeployFile(ArtifactLocation src, String deployLocation) {
      this.src = src;
      this.deployLocation = deployLocation;
    }
  }

  public final ImmutableList<IntellijPluginDeployFile> deployFiles;

  public IntellijPluginDeployInfo(ImmutableList<IntellijPluginDeployFile> deployFiles) {
    this.deployFiles = deployFiles;
  }
}
