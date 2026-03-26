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
import com.intellij.openapi.extensions.ExtensionPointName;
import java.util.Collection;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Provides a {@link BlazeCommandRunConfigurationHandler} corresponding to a given {@link
 * BlazeCommandRunConfiguration}.
 */
public interface BlazeCommandRunConfigurationHandlerProvider {

  ExtensionPointName<BlazeCommandRunConfigurationHandlerProvider> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.BlazeCommandRunConfigurationHandlerProvider");

  String getDisplayLabel();

  /**
   * Find BlazeCommandRunConfigurationHandlerProviders applicable to the given kind.
   */
  static Optional<BlazeCommandRunConfigurationHandlerProvider> findPreferredHandler(@Nullable Kind kind) {
    return EP_NAME.getExtensionList().stream().filter(it -> it.getDefaultKinds().contains(kind)).findFirst();
  }

  static Collection<BlazeCommandRunConfigurationHandlerProvider> findHandlerProviders(){
    return EP_NAME.getExtensionList();
  }

  /** Get the BlazeCommandRunConfigurationHandlerProvider with the given ID, if one exists. */
  @Nullable
  static BlazeCommandRunConfigurationHandlerProvider getHandlerProvider(@Nullable String id) {
    for (BlazeCommandRunConfigurationHandlerProvider handlerProvider : EP_NAME.getExtensions()) {
      if (handlerProvider.getId().equals(id)) {
        return handlerProvider;
      }
    }
    return null;
  }

  /** Whether this handler should appear in the user-facing handler selection dropdown. */
  default boolean isUserSelectable() { return true; }

  /** The kinds for which this handler should be selected by default. Empty for fallback/internal handlers. */
  ImmutableList<Kind> getDefaultKinds();

  /** Returns the corresponding {@link BlazeCommandRunConfigurationHandler}. */
  BlazeCommandRunConfigurationHandler createHandler(BlazeCommandRunConfiguration configuration);

  /**
   * Returns the unique ID of this {@link BlazeCommandRunConfigurationHandlerProvider}. The ID is
   * used to store configuration settings and must not change between plugin versions.
   */
  String getId();
}
