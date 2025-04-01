/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.golang.resolve;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.goide.psi.GoFile;
import com.goide.psi.impl.GoPackage;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import com.google.idea.blaze.base.command.buildresult.OutputArtifactResolver;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.GoIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.io.VfsUtils;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.lang.buildfile.references.BuildReferenceManager;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.GenericBlazeRules;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.RuleType;
import com.google.idea.blaze.base.sync.SyncCache;
import com.google.idea.blaze.base.sync.workspace.WorkspaceHelper;
import com.google.idea.blaze.base.targetmaps.ReverseDependencyMap;
import com.google.idea.blaze.golang.GoBlazeRules.RuleTypes;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.Processor;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import one.util.streamex.StreamEx;

/**
 * {@link GoPackage} specialized for blaze, with a couple of differences:
 *
 * <ol>
 *   <li>Same directory may appear in may different {@link BlazeGoPackage}s.
 *   <li>A single {@link BlazeGoPackage} may contain may different directories.
 *   <li>{@link #files} will not necessarily return all files under {@link
 *       GoPackage#getDirectories()}.
 *   <li>{@link #navigate}s to the corresponding go rule instead of a directory.
 * </ol>
 *
 * Exactly one {@link BlazeGoPackage} per go rule.
 */
public class BlazeGoPackage extends GoPackage {
  private static final Logger logger = Logger.getInstance(BlazeGoPackage.class);
  private static final String GO_TARGET_TO_FILE_MAP_KEY = "BlazeGoTargetToFileMap";

  private final Label label;
  private final String importPath;
  private final ConcurrentMap<File, Optional<PsiFile>> files;
  private final ConcurrentMap<File, Optional<VirtualFile>> directories;
  @Nullable private volatile PsiElement navigableElement;
  @Nullable private volatile PsiElement[] importReferences;

  BlazeGoPackage(
      Project project, BlazeProjectData projectData, String importPath, TargetIdeInfo target) {
    this(
        project,
        importPath,
        replaceProtoLibrary(project, projectData, target.getKey()).getLabel(),
        getTargetToFileMap(project, projectData).get(target.getKey().getLabel()));
  }

  BlazeGoPackage(Project project, String importPath, Label label, Collection<File> files) {
    super(project, getPackageName(project, files, importPath));
    this.importPath = importPath;
    this.label = label;
    this.files = new ConcurrentHashMap<>();
    files.forEach(f -> this.files.put(f, Optional.empty()));
    this.directories = new ConcurrentHashMap<>();
    files.stream()
        .map(File::getParentFile)
        .filter(Objects::nonNull)
        .forEach(f -> directories.put(f, Optional.empty()));
  }

  /**
   * The import path for proto_library doesn't match the target name, we need to replace the
   * proto_library with the corresponding go_proto_library for them to match.
   */
  private static TargetKey replaceProtoLibrary(
      Project project, BlazeProjectData projectData, TargetKey targetKey) {
    TargetMap targetMap = projectData.getTargetMap();
    TargetIdeInfo target = targetMap.get(targetKey);
    if (target == null || target.getKind() != GenericBlazeRules.RuleTypes.PROTO_LIBRARY.getKind()) {
      return targetKey;
    }
    return ReverseDependencyMap.get(project).get(targetKey).stream()
        .map(targetMap::get)
        .filter(Objects::nonNull)
        .filter(t -> t.getKind() == RuleTypes.GO_PROTO_LIBRARY.getKind())
        .map(TargetIdeInfo::getKey)
        .findFirst()
        .orElse(targetKey);
  }

  static ImmutableMultimap<Label, File> getTargetToFileMap(
      Project project, BlazeProjectData projectData) {
    ImmutableMultimap<Label, File> map =
        SyncCache.getInstance(project)
            .get(GO_TARGET_TO_FILE_MAP_KEY, BlazeGoPackage::getUncachedTargetToFileMap);
    if (map == null) {
      logger.error("Unexpected null target to file map from SyncCache.");
      return getUncachedTargetToFileMap(project, projectData);
    }
    return map;
  }

  public static ImmutableMultimap<Label, File> getUncachedTargetToFileMap(
      Project project, BlazeProjectData projectData) {
    ImmutableMultimap<Label, GoIdeInfo> libraryToTestMap = buildLibraryToTestMap(projectData);
    ImmutableMultimap.Builder<Label, File> builder = ImmutableMultimap.builder();
    for (TargetIdeInfo target : projectData.getTargetMap().targets()) {
      if (target.getGoIdeInfo() == null) {
        continue;
      }
      ImmutableSet<File> sourceFiles =
          getSourceFiles(target, project, projectData, libraryToTestMap).stream()
              .map(BlazeGoPackage::toRealFile)
              .filter(Objects::nonNull)
              .collect(toImmutableSet());
      builder.putAll(target.getKey().getLabel(), sourceFiles);
    }
    return builder.build();
  }

  /**
   * Workaround for https://github.com/bazelbuild/intellij/issues/2057. External workspace symlinks
   * can be changed externally by practically any bazel command. Such changes to symlinks will make
   * IntelliJ red. This helper resolves such symlink to an actual location.
   *
   * @see com.google.idea.blaze.java.libraries.JarCache.patchExternalFilePath()
   */
  @Nullable
  private static File toRealFile(@Nullable File maybeExternal) {
    if (maybeExternal == null) {
      return null;
    }
    // do string manipulation instead of .toPath().toRealPath().toFile()
    // because there might be a race condition and symlink won't be resolved at the time
    String externalString = maybeExternal.toString();
    if (externalString.contains("/external/")
        && !externalString.contains("/bazel-out/")
        && !externalString.contains("/blaze-out/")) {
      return new File(externalString.replaceAll("/execroot.*?/external/", "/external/"));
    }
    return maybeExternal;
  }

  private static ImmutableSet<File> getSourceFiles(
      TargetIdeInfo target,
      Project project,
      BlazeProjectData projectData,
      ImmutableMultimap<Label, GoIdeInfo> libraryToTestMap) {
    if (target.getKind() == RuleTypes.GO_WRAP_CC.getKind()) {
      return getWrapCcGoFiles(project, projectData, target);
    }
    return Stream.concat(
            Stream.of(target.getGoIdeInfo()),
            libraryToTestMap.get(target.getKey().getLabel()).stream())
        .map(GoIdeInfo::getSources)
        .flatMap(Collection::stream)
        .map(a -> resolveArtifact(project, projectData, a))
        .filter(Objects::nonNull)
        .collect(toImmutableSet());
  }

  private static ImmutableMultimap<Label, GoIdeInfo> buildLibraryToTestMap(
      BlazeProjectData projectData) {
    TargetMap targetMap = projectData.getTargetMap();
    ImmutableMultimap.Builder<Label, GoIdeInfo> builder = ImmutableMultimap.builder();
    for (TargetIdeInfo target : targetMap.targets()) {
      if (!target.getKind().hasLanguage(LanguageClass.GO)
          || target.getKind().getRuleType() != RuleType.TEST
          || target.getGoIdeInfo() == null
          || target.getGoIdeInfo().getLibraryLabels().isEmpty()) {
        continue;
      }
      for (Label label : target.getGoIdeInfo().getLibraryLabels()) {
        builder.put(label, target.getGoIdeInfo());
      }
    }
    return builder.build();
  }

  @Nullable
  private static File resolveArtifact(
      Project project, BlazeProjectData data, ArtifactLocation artifact) {
    return OutputArtifactResolver.resolve(project, data.getArtifactLocationDecoder(), artifact);
  }

  private static ImmutableSet<File> getWrapCcGoFiles(
      Project project, BlazeProjectData projectData, TargetIdeInfo target) {
    if (!target.getGoIdeInfo().getSources().isEmpty()) {
      return target.getGoIdeInfo().getSources().stream()
          .map(a -> resolveArtifact(project, projectData, a))
          .filter(Objects::nonNull)
          .collect(toImmutableSet());
    }
    // older versions of blaze don't expose the .go genfile
    // in that case, look directly in blaze-out
    String blazePackage = target.getKey().getLabel().blazePackage().relativePath();
    File directory = new File(projectData.getBlazeInfo().getGenfilesDirectory(), blazePackage);
    String filename = blazePackage + '/' + target.getKey().getLabel().targetName() + ".go";
    filename = filename.replace("_", "__");
    filename = filename.replace('/', '_');
    return ImmutableSet.of(new File(directory, filename));
  }

  /**
   * Package name is determined by package declaration in the source files (must all be the same).
   *
   * <ul>
   *   <li>for {@link RuleTypes#GO_BINARY}, it will always be {@code main}.
   *   <li>for {@link RuleTypes#GO_LIBRARY}, it is usually the last component of the import path
   *       (though not at all enforced).
   *   <li>for {@link RuleTypes#GO_TEST} it will be the same as the {@link
   *       RuleTypes#GO_BINARY}/{@link RuleTypes#GO_LIBRARY} under test, otherwise similar to a
   *       {@link RuleTypes#GO_LIBRARY} if no test subject is specified.
   *   <li>for {@link RuleTypes#GO_PROTO_LIBRARY}, it's either declared via the {@code go_package}
   *       option, or automatically generated from the target name.
   * </ul>
   */
  private static String getPackageName(Project project, Collection<File> files, String importPath) {
    PsiManager psiManager = PsiManager.getInstance(project);
    return files.stream()
        .map(file -> VfsUtils.resolveVirtualFile(file, /* refreshIfNeeded= */ false))
        .filter(Objects::nonNull)
        .map(psiManager::findFile)
        .filter(GoFile.class::isInstance)
        .map(GoFile.class::cast)
        .filter(goFile -> !Objects.equals(goFile.getBuildFlags(), "ignore"))
        .map(GoFile::getCanonicalPackageName) // strips _test suffix from test packages
        .filter(Objects::nonNull)
        .findFirst() // short circuit
        .orElseGet(() -> importPath.substring(importPath.lastIndexOf('/') + 1));
  }

  @Override
  public Set<VirtualFile> getDirectories() {
    directories.replaceAll(
        (file, oldVirtualFile) ->
            oldVirtualFile.filter(VirtualFile::isValid).isPresent()
                ? oldVirtualFile
                : Optional.ofNullable(VfsUtils.resolveVirtualFile(file, false)));
    return directories.values().stream()
        .flatMap(Streams::stream)
        .filter(VirtualFile::isValid)
        .collect(toImmutableSet());
  }

  @Override
  public StreamEx<PsiDirectory> getPsiDirectories() {
    PsiManager psiManager = PsiManager.getInstance(getProject());
    return StreamEx.of(getDirectories())
        .map(psiManager::findDirectory)
        .filter(Objects::nonNull)
        .filter(PsiDirectory::isValid);
  }

  @Override
  public Collection<PsiFile> files() {
    PsiManager psiManager = PsiManager.getInstance(getProject());
    files.replaceAll(
        (file, oldGoFile) ->
            oldGoFile.filter(PsiFile::isValid).isPresent()
                ? oldGoFile
                : Optional.ofNullable(VfsUtils.resolveVirtualFile(file, false))
                    .map(psiManager::findFile)
                    .filter(GoFile.class::isInstance));
    return files.values().stream()
        .flatMap(Streams::stream)
        .filter(PsiFile::isValid)
        .collect(toImmutableSet());
  }

  /**
   * Override {@link GoPackage#processFiles(Processor, Predicate)} to work on specific files instead
   * of by directory.
   */
  @Override
  public boolean processFiles(
      Processor<? super PsiFile> processor, Predicate<VirtualFile> virtualFileFilter) {
    if (!isValid()) {
      return true;
    }
    FileIndexFacade fileIndexFacade = FileIndexFacade.getInstance(getProject());
    for (PsiFile file : files()) {
      ProgressIndicatorProvider.checkCanceled();
      VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile.isValid()
          && virtualFileFilter.test(virtualFile)
          && !fileIndexFacade.isExcludedFile(virtualFile)
          && !processor.process(file)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void navigate(boolean requestFocus) {
    PsiElement navigable = getNavigableElement();
    if (navigable instanceof Navigatable && ((Navigatable) navigable).canNavigate()) {
      ((Navigatable) navigable).navigate(requestFocus);
    }
  }

  @Override
  public boolean canNavigate() {
    return getNavigableElement() instanceof Navigatable;
  }

  @Nullable
  @Override
  public PsiElement getNavigableElement() {
    PsiElement navigable = navigableElement;
    if (navigable != null && navigable.isValid()) {
      return navigable;
    }
    Project project = getProject();
    BuildReferenceManager buildReferenceManager = BuildReferenceManager.getInstance(project);
    PsiElement resolveLabel = buildReferenceManager.resolveLabel(label);
    return navigableElement =
        resolveLabel != null
            ? resolveLabel
            : buildReferenceManager.findBuildFile(
                WorkspaceHelper.resolveBlazePackage(project, label));
  }

  @Override
  public String getCanonicalImport(@Nullable Module contextModule) {
    return importPath;
  }

  @Override
  public String getImportPath(boolean withVendoring) {
    return importPath;
  }

  @Override
  public Collection<String> getAllImportPaths(boolean withVendoring) {
    return ImmutableList.of(importPath);
  }

  /**
   * Returns a list of resolve targets, one for each import path component.
   *
   * <p>The final path component may either be the target name, or the containing directory name (in
   * which case the target name is usually go_default_library).
   *
   * <p>E.g., for target //foo:bar with import path "github.com/user/foo/bar"
   *
   * <pre>
   *   import "github.com/user/foo/bar"
   *           (1)        (2)  (3) (4)
   * </pre>
   *
   * <ol>
   *   <li>github.com resolves to nothing
   *   <li>user resolves to nothing
   *   <li>foo resolves to directory foo/
   *   <li>bar resolves to target //foo:bar
   * </ol>
   *
   * for target //one/two:go_default_library with import path "github.com/user/one/two"
   *
   * <pre>
   *    *   import "github.com/user/one/two"
   *    *           (1)        (2)  (3) (4)
   *    * </pre>
   *
   * <ol>
   *   <li>github.com resolves to nothing
   *   <li>user resolves to nothing
   *   <li>one resolves to directory one/
   *   <li>two resolves to directory one/two/
   * </ol>
   */
  @Nullable
  PsiElement[] getImportReferences() {
    PsiElement[] references = importReferences;
    if (references != null && Arrays.stream(references).allMatch(e -> e == null || e.isValid())) {
      return references;
    }
    PsiElement navigable = getNavigableElement();
    if (navigable == null) {
      return null;
    }
    return importReferences = getImportReferences(label, navigable, importPath);
  }

  @VisibleForTesting
  static PsiElement[] getImportReferences(Label label, PsiElement buildElement, String importPath) {
    List<String> pathComponents = Splitter.on('/').splitToList(importPath);
    PsiElement[] importReferences = new PsiElement[pathComponents.size()];
    if (pathComponents.isEmpty()) {
      return importReferences;
    }
    PsiElement lastElement = getLastElement(Iterables.getLast(pathComponents), label, buildElement);
    if (lastElement == null) {
      return importReferences;
    }
    importReferences[importReferences.length - 1] = lastElement;
    PsiDirectory currentElement =
        lastElement instanceof PsiDirectory
            ? (PsiDirectory) lastElement.getParent()
            : Optional.of(lastElement)
                .map(PsiElement::getContainingFile)
                .map(PsiFile::getParent)
                .orElse(null);
    for (int i = pathComponents.size() - 2; i >= 0 && currentElement != null; --i) {
      String name = currentElement.getName();
      String pathComponent = pathComponents.get(i);
      if (Objects.equals(name, pathComponent)) {
        importReferences[i] = currentElement;
        currentElement = currentElement.getParent();
      } else {
        break;
      }
    }
    return importReferences;
  }

  @Nullable
  private static PsiElement getLastElement(String name, Label label, PsiElement buildElement) {
    if (buildElement instanceof FuncallExpression) {
      if (Objects.equals(name, label.targetName().toString())) {
        return buildElement;
      } else {
        return Optional.of(buildElement)
            .map(PsiElement::getContainingFile)
            .map(PsiFile::getParent)
            .filter(d -> Objects.equals(d.getName(), name))
            .orElse(null);
      }
    } else if (buildElement instanceof BuildFile) {
      PsiDirectory match = ((BuildFile) buildElement).getParent();
      if (match != null && Objects.equals(match.getName(), name)) {
        return match;
      }
      return buildElement;
    }
    return null;
  }

  // need to override these because GoPackage uses GoPackage#myDirectories directly in their
  // implementations

  @Override
  public boolean isValid() {
    return getDirectories().size() == directories.size();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof BlazeGoPackage)) {
      return false;
    }
    BlazeGoPackage aPackage = (BlazeGoPackage) o;
    return importPath.equals(aPackage.importPath) && directories.equals(aPackage.directories);
  }

  @Override
  public int hashCode() {
    return Objects.hash(importPath, directories);
  }

  @Override
  public String toString() {
    return "Package: " + this.importPath;
  }
}
