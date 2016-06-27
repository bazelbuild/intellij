/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.idea.blaze.android.sync.model.idea;

import com.android.builder.model.SourceProvider;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.diagnostic.Logger;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;

/**
 * Implementation of SourceProvider that is serializable. Objects used in the DSL cannot be
 * serialized.
 */
public class SourceProviderImpl implements SourceProvider, Serializable {

  private static final long serialVersionUID = 1L;

  private final String name;
  private final File manifestFile;
  private final Collection<File> resDirs;

  public SourceProviderImpl(String name,
                            File manifestFile,
                            Collection<File> resDirs) {
    this.name = name;
    this.manifestFile = manifestFile;
    this.resDirs = resDirs;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public File getManifestFile() {
    return manifestFile;
  }

  @Override
  public Collection<File> getJavaDirectories() {
    return ImmutableList.of();
  }

  @Override
  public Collection<File> getResourcesDirectories() {
    return ImmutableList.of();
  }

  @Override
  public Collection<File> getAidlDirectories() {
    return ImmutableList.of();
  }

  @Override
  public Collection<File> getRenderscriptDirectories() {
    return ImmutableList.of();
  }

  @Override
  public Collection<File> getCDirectories() {
    return ImmutableList.of();
  }

  @Override
  public Collection<File> getCppDirectories() {
    return ImmutableList.of();
  }

  @Override
  public Collection<File> getResDirectories() {
    return resDirs;
  }

  @Override
  public Collection<File> getAssetsDirectories() {
    return ImmutableList.of();
  }

  @Override
  public Collection<File> getJniLibsDirectories() {
    return ImmutableList.of();
  }

  @Override
  public Collection<File> getShadersDirectories() {
    return ImmutableList.of();
  }
}
