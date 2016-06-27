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
package com.google.idea.blaze.base.model;

import com.google.common.collect.ImmutableMap;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.Map;

/**
 * Used to save arbitrary state with the sync task.
 */
public class SyncState implements Serializable {
  private static final long serialVersionUID = 1L;
  private final ImmutableMap<String, Serializable> syncStateMap;

  @SuppressWarnings("unchecked")
  @Nullable
  public <T extends Serializable> T get(Class<T> klass) {
    return (T) syncStateMap.get(klass.getName());
  }

  public static class Builder {
    ImmutableMap.Builder<Class, Serializable> syncStateMap = ImmutableMap.builder();
    public <K extends Serializable, V extends K> Builder put(Class<K> klass, V instance) {
      syncStateMap.put(klass, instance);
      return this;
    }
    public SyncState build() {
      return new SyncState(syncStateMap.build());
    }
  }

  SyncState(ImmutableMap<Class, Serializable> syncStateMap) {
    ImmutableMap.Builder<String, Serializable> extraProjectSyncStateMap = ImmutableMap.builder();
    for (Map.Entry<Class, Serializable> entry : syncStateMap.entrySet()) {
      extraProjectSyncStateMap.put(entry.getKey().getName(), entry.getValue());
    }
    this.syncStateMap = extraProjectSyncStateMap.build();
  }
}
