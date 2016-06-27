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
package com.google.idea.blaze.base.lang.buildfile.search;

import com.google.common.collect.Lists;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.lang.buildfile.language.BuildFileType;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile.BlazeFileType;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.FunctionStatement;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.lang.buildfile.references.LabelUtils;
import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.*;
import com.intellij.psi.search.searches.ReferencesSearch.SearchParameters;
import com.intellij.util.Processor;

import javax.annotation.Nullable;
import java.util.List;

/**
 * String search for label references in BUILD files
 */
public class BuildLabelReferenceSearcher extends QueryExecutorBase<PsiReference, SearchParameters> {

  public BuildLabelReferenceSearcher() {
    super(true);
  }

  @Override
  public void processQuery(SearchParameters params, Processor<PsiReference> consumer) {
    PsiElement element = params.getElementToSearch();
    if (element instanceof FunctionStatement) {
      String fnName = ((FunctionStatement) element).getName();
      if (fnName != null) {
        searchForString(params, element, fnName);
      }
      return;
    }
    PsiFile file = ResolveUtil.asFileSearch(element);
    if (file != null) {
      processFileReferences(params, file);
      return;
    }

    if (!(element instanceof FuncallExpression)) {
      return;
    }

    Label label = ((FuncallExpression) element).resolveBuildLabel();
    PsiFile localFile = element.getContainingFile();
    if (label == null || localFile == null) {
      return;
    }
    List<String> stringsToSearch = LabelUtils.getAllValidLabelStrings(label, true);
    for (String string : stringsToSearch) {
      if (string.startsWith("//")) {
        searchForString(params, element, string);
      } else {
        // only a valid reference from local package -- restrict the search scope accordingly
        SearchScope scope = limitScopeToFile(params.getScopeDeterminedByUser(), localFile);
        if (scope != null) {
          searchForString(params, scope, element, string);
        }
      }
    }
  }

  /**
   * Find all references to the given file within BUILD files.
   */
  private void processFileReferences(SearchParameters params, PsiFile file) {
    if (file instanceof BuildFile) {
      BuildFile buildFile = (BuildFile) file;
      processBuildFileReferences(params, buildFile);
      if (buildFile.getBlazeFileType() == BlazeFileType.BuildPackage) {
        return;
      }
      // for skylark extensions, we also check for package-local references, below
    }
    BlazePackage blazePackage = BlazePackage.getContainingPackage(file);
    PsiDirectory directory = blazePackage != null ? blazePackage.getContainingDirectory() : null;
    if (directory == null) {
      return;
    }
    Label label = LabelUtils.createLabelForFile(blazePackage, PsiUtils.getFilePath(file));
    if (label == null) {
      return;
    }

    // files can only be directly referenced in the containing blaze package
    List<String> stringsToSearch = LabelUtils.getAllValidLabelStrings(label, true);
    SearchScope scope = params.getScopeDeterminedByUser()
      .intersectWith(blazePackage.getSearchScope(true));

    for (String string : stringsToSearch) {
      searchForString(params, scope, file, string);
    }
  }

  /**
   * Find references to both the file itself, and build targets defined in the file.
   */
  private void processBuildFileReferences(SearchParameters params, BuildFile file) {
    WorkspacePath workspacePath = file.getPackageWorkspacePath();
    if (workspacePath == null) {
      return;
    }
    List<String> stringsToSearch = Lists.newArrayList();
    if (file.getBlazeFileType() == BlazeFileType.BuildPackage) {
      stringsToSearch.add("//" + workspacePath);
    } else {
      stringsToSearch.add("//" + workspacePath + ":" + file.getName());
      stringsToSearch.add("//" + workspacePath + "/" + file.getName()); // deprecated load/subinclude format
    }
    for (String string : stringsToSearch) {
      searchForString(params, file, string);
    }
  }

  /**
   * Search for package-local references.<br>
   * Returns null if the resulting scope is empty
   */
  @Nullable
  private static SearchScope limitScopeToFile(SearchScope scope, PsiFile file) {
    if (scope instanceof LocalSearchScope) {
      return ((LocalSearchScope) scope).isInScope(file.getVirtualFile()) ? new LocalSearchScope(file) : null;
    }
    return scope.intersectWith(new LocalSearchScope(file));
  }

  private static void searchForString(SearchParameters params, PsiElement element, String string) {
    searchForString(params, params.getScopeDeterminedByUser(), element, string);
  }

  private static void searchForString(SearchParameters params, SearchScope scope, PsiElement element, String string) {
    if (scope instanceof GlobalSearchScope) {
      scope = GlobalSearchScope.getScopeRestrictedByFileTypes((GlobalSearchScope) scope, BuildFileType.INSTANCE);
    }
    params.getOptimizer().searchWord(string, scope, UsageSearchContext.IN_STRINGS, true, element);
  }

}
