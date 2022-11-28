/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.gazelle;

import com.google.idea.blaze.base.model.primitives.InvalidTargetException;
import com.google.idea.blaze.base.model.primitives.Label;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(
    name = "GazelleUserSettings",
    storages = {@Storage("gazelle.user.settings.xml")})
public class GazelleUserSettings implements PersistentStateComponent<GazelleUserSettings> {

  private static final String EMPTY_GAZELLE_TARGET = "";

  private static final boolean GAZELLE_HEADLESS_MODE = false;

  private String gazelleTarget = EMPTY_GAZELLE_TARGET;

  // Headless mode will not try to set up the UI at all.
  // Used mainly for testing,
  // not registered in the global configuration UI on purpose,
  private boolean gazelleHeadless = GAZELLE_HEADLESS_MODE;

  public static GazelleUserSettings getInstance() {
    return ServiceManager.getService(GazelleUserSettings.class);
  }

  public boolean shouldRunHeadless() {
    return gazelleHeadless;
  }

  public void setGazelleHeadless(boolean headless) {
    gazelleHeadless = headless;
  }

  public void clearGazelleTarget() {
    this.gazelleTarget = EMPTY_GAZELLE_TARGET;
  }

  public String getGazelleTarget() {
    return StringUtil.defaultIfEmpty(this.gazelleTarget, EMPTY_GAZELLE_TARGET).trim();
  }

  public void setGazelleTarget(String target) {
    this.gazelleTarget = target;
  }

  public Optional<Label> getGazelleTargetLabel() throws InvalidTargetException {
    String rawTarget = getGazelleTarget();
    if (rawTarget.equals(EMPTY_GAZELLE_TARGET)) {
      return Optional.empty();
    }
    String validationError = Label.validate(rawTarget);
    if ( validationError != null) {
      throw new InvalidTargetException(validationError);
    }
    Label targetLabel = Label.create(rawTarget);
    return Optional.of(targetLabel);
  }

  @Override
  public @Nullable GazelleUserSettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull GazelleUserSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
