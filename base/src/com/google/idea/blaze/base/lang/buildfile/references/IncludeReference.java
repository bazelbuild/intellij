/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.lang.buildfile.references;

import com.google.idea.blaze.base.lang.buildfile.completion.BuildLookupElement;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.StringLiteral;
import com.google.idea.blaze.base.lang.buildfile.search.BlazePackage;
import com.google.idea.blaze.base.model.primitives.Label;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.util.ArrayUtil;

import javax.annotation.Nullable;

/** Resolves include statements in MODULE.bazel files to *.MODULE.bazel fragment files. */
public class IncludeReference extends PsiReferenceBase<StringLiteral> {

  public IncludeReference(StringLiteral element, boolean soft) {
    super(element, new TextRange(0, element.getTextLength()), soft);
  }

  @Nullable
  @Override
  public PsiElement resolve() {
    String path = myElement.getStringContents();
    if (path == null) {
      return null;
    }

    Label label = getLabel(path);
    if (label == null) {
      return null;
    }

    return getReferenceManager().resolveLabel(label);
  }

  @Override
  public Object[] getVariants() {
    String labelString = LabelUtils.trimToDummyIdentifier(myElement.getStringContents());
    return getFileLookups(labelString);
  }

  private BuildLookupElement[] getFileLookups(String labelString) {
    if (labelString.startsWith("//") || labelString.equals("/") || labelString.startsWith("@")) {
      return getNonLocalFileLookups(labelString);
    }
    return getPackageLocalFileLookups(labelString);
  }

  /**
   * Handle non-local labels like "//:foo", "//pkg:foo", or "@repo//pkg:foo". Combines directory
   * navigation (before colon) with file completion (after colon).
   */
  private BuildLookupElement[] getNonLocalFileLookups(String labelString) {
    // File completion within resolved package (handles case after colon)
    BuildLookupElement[] includeFileLookups = getIncludeFileLookups(labelString);

    // Package/directory navigation (handles case before colon)
    // Note: nonLocalFileLookup returns null when label contains colon, by design
    FileLookupData lookupData = FileLookupData.nonLocalFileLookup(labelString, myElement);
    BuildLookupElement[] packageLookups =
        lookupData != null
            ? getReferenceManager().resolvePackageLookupElements(lookupData)
            : BuildLookupElement.EMPTY_ARRAY;

    return ArrayUtil.mergeArrays(includeFileLookups, packageLookups);
  }

  /**
   * Get *.MODULE.bazel file completions within a resolved package. Handles "//:foo<CARET>",
   * "//pkg:foo<CARET>", "@repo//pkg:foo<CARET>".
   */
  private BuildLookupElement[] getIncludeFileLookups(String labelString) {
    String packagePrefix = LabelUtils.getPackagePathComponent(labelString);
    String externalWorkspace = LabelUtils.getExternalWorkspaceComponent(labelString);
    BuildFile parentFile = myElement.getContainingFile();
    if (parentFile == null) {
      return BuildLookupElement.EMPTY_ARRAY;
    }

    BlazePackage containingPackage = BlazePackage.getContainingPackage(parentFile);
    if (containingPackage == null) {
      return BuildLookupElement.EMPTY_ARRAY;
    }

    BuildFile referencedBuildFile =
        LabelUtils.getReferencedBuildFile(
            containingPackage.buildFile, externalWorkspace, packagePrefix);

    boolean hasColon = labelString.indexOf(':') != -1;
    VirtualFileFilter filter =
        file ->
            (file.getName().endsWith(".MODULE.bazel")
                && !file.getName().equals("MODULE.bazel")
                && !file.getPath().equals(parentFile.getFilePath()))
                || (hasColon && file.isDirectory());

    FileLookupData lookupData =
        FileLookupData.packageLocalFileLookup(labelString, myElement, referencedBuildFile, filter);

    return lookupData != null
        ? getReferenceManager().resolvePackageLookupElements(lookupData)
        : BuildLookupElement.EMPTY_ARRAY;
  }

  /** Handle package-local labels like ":foo" or "foo". */
  private BuildLookupElement[] getPackageLocalFileLookups(String labelString) {
    BuildFile parentFile = myElement.getContainingFile();
    if (parentFile == null) {
      return BuildLookupElement.EMPTY_ARRAY;
    }

    BlazePackage containingPackage = BlazePackage.getContainingPackage(parentFile);
    if (containingPackage == null) {
      return BuildLookupElement.EMPTY_ARRAY;
    }

    boolean hasColon = labelString.indexOf(':') != -1;
    VirtualFileFilter filter =
        file ->
            (file.getName().endsWith(".MODULE.bazel")
                && !file.getName().equals("MODULE.bazel")
                && !file.getPath().equals(parentFile.getFilePath()))
                || (hasColon && file.isDirectory());

    FileLookupData lookupData =
        FileLookupData.packageLocalFileLookup(
            labelString, myElement, containingPackage.buildFile, filter);

    return lookupData != null
        ? getReferenceManager().resolvePackageLookupElements(lookupData)
        : BuildLookupElement.EMPTY_ARRAY;
  }

  private BuildReferenceManager getReferenceManager() {
    return BuildReferenceManager.getInstance(myElement.getProject());
  }

  @Nullable
  private Label getLabel(String labelString) {
    if (labelString.indexOf('*') != -1) {
      // don't handle globs
      return null;
    }
    BlazePackage blazePackage = myElement.getBlazePackage();
    return LabelUtils.createLabelFromString(blazePackage, labelString);
  }
}
