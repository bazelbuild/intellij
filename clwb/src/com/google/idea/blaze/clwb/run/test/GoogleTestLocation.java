/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.clwb.run.test;

import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.syncstatus.SyncStatusContributor;
import com.google.idea.sdkcompat.cidr.OCSymbolAdapter;
import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.cidr.execution.testing.google.CidrGoogleTestUtil;
import com.jetbrains.cidr.lang.OCFileTypeHelpers;
import com.jetbrains.cidr.lang.psi.OCCppNamespace;
import com.jetbrains.cidr.lang.psi.OCFile;
import com.jetbrains.cidr.lang.psi.OCFunctionDefinition;
import com.jetbrains.cidr.lang.psi.OCMacroCall;
import com.jetbrains.cidr.lang.psi.OCMacroCallArgument;
import com.jetbrains.cidr.lang.psi.OCStruct;
import com.jetbrains.cidr.lang.psi.visitors.OCVisitor;
import com.jetbrains.cidr.lang.symbols.OCSymbol;
import com.jetbrains.cidr.lang.symbols.cpp.OCFunctionSymbol;
import com.jetbrains.cidr.lang.symbols.cpp.OCStructSymbol;
import com.jetbrains.cidr.lang.symbols.cpp.OCSymbolWithQualifiedName;
import com.jetbrains.cidr.lang.symbols.symtable.FileSymbolTablesCache;
import com.jetbrains.cidr.lang.ui.OCLongActionUtil;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;

/** A {@link PsiLocation} with corresponding gtest specification */
public class GoogleTestLocation extends PsiLocation<PsiElement> {

  public final GoogleTestSpecification gtest;
  @Nullable public final String testFilter;

  GoogleTestLocation(PsiElement psi, GoogleTestSpecification gtest) {
    super(psi);
    this.gtest = gtest;
    this.testFilter = gtest.testFilter();
  }

  /** The raw test filter string with '--test_filter=' prepended, or null if there is no filter. */
  @Nullable
  public String getTestFilterFlag() {
    return testFilter != null ? BlazeFlags.TEST_FILTER + "=" + testFilter : null;
  }

  @Nullable
  public static GoogleTestLocation findGoogleTest(Location<?> location, Project project) {
    if (location instanceof GoogleTestLocation) {
      return (GoogleTestLocation) location;
    }
    return findGoogleTest(location.getPsiElement(), project);
  }

  @Nullable
  public static GoogleTestLocation findGoogleTest(PsiElement element, Project project) {
    // Copied from on CidrGoogleTestRunConfigurationProducer::findTestObject.
    // Precedence order (decreasing): class/function, macro, file
    PsiElement parent =
        PsiTreeUtil.getNonStrictParentOfType(element, OCFunctionDefinition.class, OCStruct.class);

    OCStructSymbol parentSymbol;
    if (parent instanceof OCStruct
        && ((parentSymbol = ((OCStruct) parent).getSymbol()) != null)
        && CidrGoogleTestUtil.isGoogleTestClass(parentSymbol, project)) {
      Couple<String> name = CidrGoogleTestUtil.extractGoogleTestName(parentSymbol, project);
      if (name != null) {
        return createFromClassAndMethod(parent, name.first, name.second);
      }
      String className = parentSymbol.getQualifiedName().getName();
      return createFromClass(parent, className);
    } else if (parent instanceof OCFunctionDefinition) {
      OCFunctionSymbol symbol = ((OCFunctionDefinition) parent).getSymbol();
      if (symbol != null) {
        // #api182 change
        @SuppressWarnings("rawtypes")
        OCSymbolWithQualifiedName resolvedOwner = OCSymbolAdapter.getResolvedOwner(symbol, project);
        if (resolvedOwner != null) {
          // #api182 change
          @SuppressWarnings("rawtypes")
          OCSymbol owner = OCSymbolAdapter.getDefinitionSymbol(resolvedOwner, project);
          if (owner instanceof OCStructSymbol
              && CidrGoogleTestUtil.isGoogleTestClass((OCStructSymbol) owner, project)) {
            OCStruct struct = (OCStruct) OCSymbolAdapter.locateDefinition(owner, project);
            Couple<String> name =
                CidrGoogleTestUtil.extractGoogleTestName((OCStructSymbol) owner, project);
            if (name != null) {
              return createFromClassAndMethod(struct, name.first, name.second);
            }
            return createFromClass(struct, ((OCStructSymbol) owner).getQualifiedName().getName());
          }
        }
      }
    }

    // if we're still here, let's test for a macro and, as a last resort, a file.
    parent = PsiTreeUtil.getNonStrictParentOfType(element, OCMacroCall.class, OCFile.class);
    if (parent instanceof OCMacroCall) {
      OCMacroCall gtestMacro = CidrGoogleTestUtil.findGoogleTestMacros(parent);
      if (gtestMacro != null) {
        List<OCMacroCallArgument> arguments = gtestMacro.getArguments();
        if (arguments.size() >= 2) {
          OCMacroCallArgument suiteArg = arguments.get(0);
          OCMacroCallArgument testArg = arguments.get(1);

          // if the element is the first argument of macro call,
          // then running entire suite, otherwise only a current test
          boolean isSuite =
              isFirstArgument(PsiTreeUtil.getParentOfType(element, OCMacroCallArgument.class))
                  || isFirstArgument(element.getPrevSibling());
          String suiteName = CidrGoogleTestUtil.extractArgumentValue(suiteArg);
          String testName = CidrGoogleTestUtil.extractArgumentValue(testArg);
          OCStructSymbol symbol =
              CidrGoogleTestUtil.findGoogleTestSymbol(element.getProject(), suiteName, testName);
          if (symbol != null) {
            OCStruct targetElement = (OCStruct) OCSymbolAdapter.locateDefinition(symbol, project);
            return createFromClassAndMethod(targetElement, suiteName, isSuite ? null : testName);
          }
        }
      }
      Couple<String> suite = CidrGoogleTestUtil.extractFullSuiteNameFromMacro(parent);
      if (suite != null) {
        Collection<OCStructSymbol> res =
            CidrGoogleTestUtil.findGoogleTestSymbolsForSuiteRandomly(
                element.getProject(), suite.first, true);
        if (!res.isEmpty()) {
          OCStruct struct =
              (OCStruct) OCSymbolAdapter.locateDefinition(res.iterator().next(), project);
          GoogleTestSpecification gtest =
              new GoogleTestSpecification.FromPsiElement(suite.first, null, suite.second, null);
          return new GoogleTestLocation(struct, gtest);
        }
      }
    } else if (parent instanceof OCFile && mayBeGoogleTestFile((OCFile) parent)) {
      return createFromFile(parent);
    }
    return null;
  }

  private static boolean isFirstArgument(@Nullable PsiElement element) {
    OCMacroCall macroCall = PsiTreeUtil.getParentOfType(element, OCMacroCall.class);
    if (macroCall != null) {
      List<OCMacroCallArgument> arguments = macroCall.getArguments();
      return !arguments.isEmpty() && arguments.get(0).equals(element);
    }
    return false;
  }

  /** Returns true if a file may contain googletest cases. */
  private static boolean mayBeGoogleTestFile(OCFile file) {
    // googletest files should be cc files since it eventually needs to compile+link into a binary
    if (OCFileTypeHelpers.isHeaderFile(file.getName())) {
      return false;
    }
    if (!areSymbolsPrecalculated(file.getProject())) {
      // If symbols are not up to date, fileIncludesGoogleTest might block on our "friend"
      // ensurePendingFilesProcessed(), which would freeze the AWT, so just say "maybe".
      return true;
    }
    boolean transitivelyIncludesGtestHeader =
        CachedValuesManager.getCachedValue(
            file,
            () -> {
              // We're not 100% sure this won't block since we don't have access to
              // "FileSymbolTablesCache.getInstance(project).isUpToDate()", so wrap in timeout.
              // Throws ProcessCanceledException if timed out or canceled.
              Boolean doesInclude =
                  OCLongActionUtil.execWithTimeoutProgressInDispatch(
                      "progressbar.long.resolve.description",
                      OCLongActionUtil.TIMEOUT_PROPERTY,
                      file.getProject(),
                      () -> CidrGoogleTestUtil.fileIncludesGoogleTest(file));
              return CachedValueProvider.Result.create(
                  doesInclude, PsiModificationTracker.MODIFICATION_COUNT);
            });
    if (transitivelyIncludesGtestHeader) {
      return true;
    }
    // Symbols and the import graph may not be accurate for unsynced files or files outside
    // of source roots. Just do a heuristic search on the AST for minimal support.
    return unsyncedFileContainsGtestMacroCalls(file);
  }

  private static boolean unsyncedFileContainsGtestMacroCalls(OCFile file) {
    return CachedValuesManager.getCachedValue(
        file,
        () ->
            CachedValueProvider.Result.create(
                computeUnsyncedFileContainsGtestMacroCalls(file),
                PsiModificationTracker.MODIFICATION_COUNT));
  }

  private static boolean computeUnsyncedFileContainsGtestMacroCalls(OCFile file) {
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) {
      return false;
    }
    if (ProjectFileIndex.getInstance(file.getProject()).isInSource(virtualFile)) {
      if (!SyncStatusContributor.isUnsynced(file.getProject(), virtualFile)) {
        return false;
      }
    }
    MacroCallLocator locator = new MacroCallLocator();
    file.accept(locator);
    return locator.foundGtestMacroCall;
  }

  /** Are symbols ready to be used without blocking? */
  private static boolean areSymbolsPrecalculated(Project project) {
    return FileSymbolTablesCache.areSymbolsLoaded(project);
    // #api182: also check "&& FileSymbolTablesCache.getInstance(project).isUpToDate()";
  }

  @Nullable
  private static GoogleTestLocation createFromFile(@Nullable PsiElement element) {
    return createFromClassAndMethod(element, null, null);
  }

  @Nullable
  private static GoogleTestLocation createFromClass(
      @Nullable PsiElement element, @Nullable String className) {
    return createFromClassAndMethod(element, className, null);
  }

  @Nullable
  private static GoogleTestLocation createFromClassAndMethod(
      @Nullable PsiElement element, @Nullable String classOrSuiteName, @Nullable String testName) {
    if (element == null) {
      return null;
    }
    GoogleTestSpecification gtest =
        new GoogleTestSpecification.FromPsiElement(classOrSuiteName, testName, null, null);
    return new GoogleTestLocation(element, gtest);
  }

  /**
   * Searches a file for usages of googletest macros as a hint that this is a googletest file. Does
   * not do any resolving and does not use symbol tables, so we aren't necessarily sure that the
   * macro is defined by googletest headers -- we only know the name matches.
   */
  private static class MacroCallLocator extends OCVisitor {
    boolean foundGtestMacroCall = false;

    @Override
    public void visitOCFile(OCFile file) {
      visitRecursively(file);
    }

    @Override
    public void visitMacroCall(OCMacroCall macroCall) {
      if (CidrGoogleTestUtil.findGoogleTestMacros(macroCall) != null) {
        foundGtestMacroCall = true;
      }
      visitRecursively(macroCall);
    }

    @Override
    public void visitNamespace(OCCppNamespace namespace) {
      visitRecursively(namespace);
    }

    // The macros generate class definitions, and it's not clear that anyone would actually
    // generate classes in odd places like inside other classes or within functions (though legal)
    // so only recurse into namespaces and other macro calls for now.
    private void visitRecursively(PsiElement element) {
      PsiElement child = element.getFirstChild();
      while (child != null && !foundGtestMacroCall) {
        child.accept(this);
        child = child.getNextSibling();
      }
    }
  }
}
