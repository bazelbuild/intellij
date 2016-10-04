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
package com.google.idea.blaze.base.lang.buildfile.completion;

import com.google.common.collect.Maps;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildElement;
import com.google.idea.blaze.base.lang.buildfile.psi.StringLiteral;
import com.google.idea.blaze.base.lang.buildfile.references.QuoteType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.util.Processor;
import java.util.Collection;
import java.util.Map;

/** Collects completion results, removing duplicate entries. */
public class CompletionResultsProcessor implements Processor<BuildElement> {

  private final Map<String, LookupElement> results = Maps.newHashMap();
  private final PsiElement originalElement;
  private final QuoteType quoteType;

  public CompletionResultsProcessor(PsiElement originalElement, QuoteType quoteType) {
    this.originalElement = originalElement;
    this.quoteType = quoteType;
  }

  @Override
  public boolean process(BuildElement buildElement) {
    if (buildElement == originalElement) {
      return true;
    }
    if (buildElement instanceof StringLiteral) {
      StringLiteral literal = (StringLiteral) buildElement;
      results.put(
          literal.getStringContents(),
          new StringLiteralReferenceLookupElement((StringLiteral) buildElement, quoteType));
    } else if (buildElement instanceof PsiNamedElement) {
      PsiNamedElement namedElement = (PsiNamedElement) buildElement;
      results.put(
          namedElement.getName(),
          new NamedBuildLookupElement((PsiNamedElement) buildElement, quoteType));
    }
    return true;
  }

  public Collection<LookupElement> getResults() {
    return results.values();
  }
}
