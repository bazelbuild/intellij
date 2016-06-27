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
package com.google.idea.blaze.java.sync.model;

import com.google.idea.blaze.base.projectview.section.Glob;

import java.io.Serializable;

/**
 * Sync data for the java plugin.
 */
public class BlazeJavaSyncData implements Serializable {
  private static final long serialVersionUID = 2L;

  public final BlazeJavaImportResult importResult;
  public final Glob.GlobSet excludedLibraries;
  public final boolean attachSourceJarsByDefault;

  public BlazeJavaSyncData(BlazeJavaImportResult importResult,
                           Glob.GlobSet excludedLibraries,
                           boolean attachSourceJarsByDefault) {
    this.importResult = importResult;
    this.excludedLibraries = excludedLibraries;
    this.attachSourceJarsByDefault = attachSourceJarsByDefault;
  }
}
