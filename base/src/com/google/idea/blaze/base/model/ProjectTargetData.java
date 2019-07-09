/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.model;

import com.google.auto.value.AutoValue;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.sync.aspects.BlazeIdeInterfaceState;
import javax.annotation.Nullable;

/** Project data relating to targets and their generated outputs. */
@AutoValue
public abstract class ProjectTargetData {

  public abstract TargetMap getTargetMap();

  @Nullable
  public abstract BlazeIdeInterfaceState getIdeInterfaceState();

  public abstract RemoteOutputArtifacts getRemoteOutputs();

  public static ProjectTargetData create(
      TargetMap targetMap,
      @Nullable BlazeIdeInterfaceState state,
      RemoteOutputArtifacts remoteOutputs) {
    return new AutoValue_ProjectTargetData(targetMap, state, remoteOutputs);
  }
}
