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
package com.google.idea.blaze.base.lang.projectview.completion;

import com.google.idea.blaze.base.lang.projectview.language.ProjectViewLanguage;
import com.google.idea.blaze.base.lang.projectview.psi.ProjectViewPsiListSection;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.projectview.section.sections.AdditionalLanguagesSection;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.StandardPatterns;
import com.intellij.util.ProcessingContext;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * Code completion for additional language types.
 */
public class AdditionalLanguagesCompletionContributor extends CompletionContributor {

  @Override
  public AutoCompletionDecision handleAutoCompletionPossibility(AutoCompletionContext context) {
    // auto-insert the obvious only case; else show other cases.
    final LookupElement[] items = context.getItems();
    if (items.length == 1) {
      return AutoCompletionDecision.insertItem(items[0]);
    }
    return AutoCompletionDecision.SHOW_LOOKUP;
  }

  public AdditionalLanguagesCompletionContributor() {
    extend(
      CompletionType.BASIC,
      psiElement()
        .withLanguage(ProjectViewLanguage.INSTANCE)
        .inside(
          psiElement(ProjectViewPsiListSection.class)
            .withText(StandardPatterns.string().startsWith(AdditionalLanguagesSection.KEY.getName()))),
      new CompletionProvider<CompletionParameters>() {
        @Override
        protected void addCompletions(CompletionParameters parameters, ProcessingContext context, CompletionResultSet result) {
          for (LanguageClass type : LanguageClass.values()) {
            result.addElement(LookupElementBuilder.create(type.getName()));
          }
        }
      }
    );
  }
}
