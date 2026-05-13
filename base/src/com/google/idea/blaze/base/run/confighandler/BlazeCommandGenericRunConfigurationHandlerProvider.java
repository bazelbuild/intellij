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
package com.google.idea.blaze.base.run.confighandler;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;

/**
 * Generic handler provider for {@link BlazeCommandRunConfiguration}s, used as a fallback in the
 * case where no other handler providers are more relevant.
 */
public class BlazeCommandGenericRunConfigurationHandlerProvider implements BlazeCommandRunConfigurationHandlerProvider {

  public static BlazeCommandGenericRunConfigurationHandlerProvider getInstance() {
    return BlazeCommandRunConfigurationHandlerProvider.EP_NAME
        .findExtension(BlazeCommandGenericRunConfigurationHandlerProvider.class);
  }

  /** Returns the registered instance, or creates a new one if extension points aren't available (e.g., in tests). */
  public static BlazeCommandGenericRunConfigurationHandlerProvider getInstanceOrCreate() {
    BlazeCommandGenericRunConfigurationHandlerProvider instance = getInstance();
    return instance != null ? instance : new BlazeCommandGenericRunConfigurationHandlerProvider();
  }

  @Override
  public String getDisplayLabel() {
    return "Generic Command";
  }

  @Override
  public ImmutableList<Kind> getDefaultKinds() {
    return ImmutableList.of();
  }

  @Override
  public BlazeCommandRunConfigurationHandler createHandler(BlazeCommandRunConfiguration config) {
    return new BlazeCommandGenericRunConfigurationHandler(config);
  }

  @Override
  public String getId() {
    return "BlazeCommandGenericRunConfigurationHandlerProvider";
  }
}
