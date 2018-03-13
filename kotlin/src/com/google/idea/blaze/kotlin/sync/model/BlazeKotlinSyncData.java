/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.kotlin.sync.model;

import com.google.idea.blaze.base.model.BlazeProjectData;
import java.io.Serializable;
import javax.annotation.concurrent.Immutable;

/** Sync data for the kotlin plugin. */
@Immutable
public class BlazeKotlinSyncData implements Serializable {
  private static final long serialVersionUID = 1L;

  public final BlazeKotlinImportResult importResult;

  public BlazeKotlinSyncData(BlazeKotlinImportResult importResult) {
    this.importResult = importResult;
  }

  public static BlazeKotlinSyncData get(BlazeProjectData blazeProjectData) {
    return blazeProjectData.syncState.get(BlazeKotlinSyncData.class);
  }
}
