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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.settings.ui.BlazeUserSettingsCompositeConfigurable;
import com.google.idea.common.settings.AutoConfigurable;
import com.google.idea.common.settings.ConfigurableSetting;
import com.google.idea.common.settings.SearchableText;
import com.google.idea.common.settings.SettingComponent;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.ui.TextFieldWithStoredHistory;
import com.intellij.util.PlatformUtils;

class GazelleUserSettingsConfigurable extends AutoConfigurable {
  static class UiContributor implements BlazeUserSettingsCompositeConfigurable.UiContributor {

    @Override
    public UnnamedConfigurable getConfigurable() {
      return new GazelleUserSettingsConfigurable();
    }

    @Override
    public ImmutableCollection<SearchableText> getSearchableText() {
      return SearchableText.collect(SETTINGS);
    }
  }

  private static boolean shouldShow() {
    return PlatformUtils.isIdeaUltimate() || PlatformUtils.isGoIde();
  }

  private static final String GAZELLE_TARGET_KEY = "gazelle.target";
  private static final ConfigurableSetting<?, ?> GAZELLE_TARGET =
      ConfigurableSetting.builder(GazelleUserSettings::getInstance)
          .label("Gazelle target to run on Sync.")
          .getter(GazelleUserSettings::getGazelleTarget)
          .setter(GazelleUserSettings::setGazelleTarget)
          .hideIf(() -> !shouldShow())
          .componentFactory(
              SettingComponent.LabeledComponent.factory(
                  () -> new TextFieldWithStoredHistory(GAZELLE_TARGET_KEY),
                  s -> Strings.nullToEmpty(s.getText()).trim(),
                  TextFieldWithStoredHistory::setTextAndAddToHistory));

  private static final ImmutableList<ConfigurableSetting<?, ?>> SETTINGS =
      ImmutableList.of(GAZELLE_TARGET);

  private GazelleUserSettingsConfigurable() {
    super(SETTINGS);
  }
}
