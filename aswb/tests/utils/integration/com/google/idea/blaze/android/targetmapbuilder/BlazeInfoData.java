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
package com.google.idea.blaze.android.targetmapbuilder;

import com.google.idea.blaze.base.sync.BlazeSyncIntegrationTestCase;

/** Environment containing the common Blaze attributes shared across a target map. */
public class BlazeInfoData {
  public static final BlazeInfoData DEFAULT = builder().build();
  private String blazeExecutablesRootPath;

  public BlazeInfoData(String blazeExecutablesRootPath) {
    this.blazeExecutablesRootPath = blazeExecutablesRootPath;
  }

  public String getBlazeExecutablesRootPath() {
    return blazeExecutablesRootPath;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder for BlazeInfoData. */
  public static class Builder {
    /**
     * The default value is taken from {@link BlazeSyncIntegrationTestCase#BLAZE_BIN} to keep target
     * maps default consistent with existing blaze integration tests.
     */
    public static String DEFAULT_BLAZE_EXECUTABLES_ROOT_PATH =
        "/execroot/root/blaze-out/gcc-4.X.Y-crosstool-v17-hybrid-grtev3-k8-fastbuild/bin";

    private String blazeExecutablesRootPath = DEFAULT_BLAZE_EXECUTABLES_ROOT_PATH;

    /** For the default value {@see BlazeInfoData.Builder#DEFAULT_BLAZE_EXECUTABLES_ROOT_PATH} */
    public Builder setBlazeExecutablesRootPath(String blazeExecutablesRootPath) {
      this.blazeExecutablesRootPath = blazeExecutablesRootPath;
      return this;
    }

    public BlazeInfoData build() {
      return new BlazeInfoData(blazeExecutablesRootPath);
    }
  }
}
