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
package com.google.idea.common.settings;

import com.intellij.ide.ui.search.SearchableOptionsRegistrar;
import com.intellij.openapi.options.SearchableConfigurable;
import java.util.Set;
import javax.annotation.Nullable;

/** A helper class to make settings text searchable. */
public final class SearchableTextHelper {

  private final SearchableOptionsRegistrar registrar;
  private final String configurableId;
  private final String displayName;

  public SearchableTextHelper(SearchableConfigurable configurable) {
    this(configurable.getId(), configurable.getDisplayName());
  }

  public SearchableTextHelper(String configurableId, String displayName) {
    this.registrar = SearchableOptionsRegistrar.getInstance();
    this.configurableId = configurableId;
    this.displayName = displayName;
  }

  /**
   * Registers the given text, making it searchable for all words in its {@link
   * SearchableText#label()} and {@link SearchableText#tags()}.
   */
  public void registerText(SearchableText text) {
    registerWords(
        registrar.getProcessedWordsWithoutStemming(text.label()), /* searchResult= */ text.label());
    registerWords(text.tags(), /* searchResult= */ text.label());
  }

  private void registerWords(Set<String> words, @Nullable String searchResult) {
    words.forEach(
        word ->
            registrar.addOption(word, /* path= */ null, searchResult, configurableId, displayName));
  }
}
