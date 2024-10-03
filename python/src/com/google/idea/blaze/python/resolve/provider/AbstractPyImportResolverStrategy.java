/*
 * Copyright 2017-2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.python.resolve.provider;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.idea.blaze.base.command.buildresult.OutputArtifactResolver;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.PyIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.sync.SyncCache;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.python.resolve.BlazePyResolverUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.imports.AutoImportQuickFix;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.resolve.PyQualifiedNameResolveContext;
import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * An abstract base class for {@link PyImportResolverStrategy}, for the most common case where
 * import strings are resolved to python packages and modules.
 */
public abstract class AbstractPyImportResolverStrategy implements PyImportResolverStrategy {

  private final static Path PATH_CURRENT_DIR = Path.of(".");

  private final ArtifactSupplierToPsiElementProviderMapper artifactSupplierToPsiElementProviderMapper;

  public AbstractPyImportResolverStrategy() {
    this(new DefaultArtifactSupplierToPsiElementProviderMapper());
  }

  /**
   * This constructor is exposed package-private for testing purposes.
   * @param artifactSupplierToPsiElementProviderMapper is supplied so that it is possible to mock.
   */
  AbstractPyImportResolverStrategy(
      ArtifactSupplierToPsiElementProviderMapper artifactSupplierToPsiElementProviderMapper) {
    this.artifactSupplierToPsiElementProviderMapper = artifactSupplierToPsiElementProviderMapper;
  }

  @Nullable
  @Override
  public final PsiElement resolveFromSyncData(
      QualifiedName name, PyQualifiedNameResolveContext context) {
    PySourcesIndex index = getSourcesIndex(context.getProject());
    if (index == null) {
      return null;
    }
    PsiElementProvider resolver = index.sourceMap.get(name);
    return resolver != null ? resolver.get(context.getPsiManager()) : null;
  }

  @Override
  public final void addImportCandidates(
      PsiReference reference, String name, AutoImportQuickFix quickFix) {
    Project project = reference.getElement().getProject();
    PySourcesIndex index = getSourcesIndex(project);
    if (index == null) {
      return;
    }
    PsiManager psiManager = PsiManager.getInstance(project);
    for (QualifiedName candidate : index.shortNames.get(name)) {
      PsiElementProvider resolver = index.sourceMap.get(candidate);
      if (resolver == null) {
        continue;
      }
      PsiElement psi = PyUtil.turnDirIntoInit(resolver.get(psiManager));
      if (psi == null) {
        continue;
      }
      PsiFile file = psi.getContainingFile();
      if (file != null && psi instanceof PsiNamedElement) {
        quickFix.addImport((PsiNamedElement) psi, file, candidate.removeLastComponent());
      }
    }
  }

  @Nullable
  private PySourcesIndex getSourcesIndex(Project project) {
    if (Blaze.getProjectType(project) == ProjectType.QUERY_SYNC) {
      return null;
    }
    return SyncCache.getInstance(project).get(getClass(), this::buildSourcesIndex);
  }

  // exposed package-private for testing
  @SuppressWarnings("unused")
  PySourcesIndex buildSourcesIndex(Project project, BlazeProjectData projectData) {
    ImmutableSetMultimap.Builder<String, QualifiedName> shortNames = ImmutableSetMultimap.builder();
    Map<QualifiedName, PsiElementProvider> map = new HashMap<>();
    ArtifactLocationDecoder decoder = projectData.getArtifactLocationDecoder();
    for (TargetIdeInfo target : projectData.getTargetMap().targets()) {
      List<QualifiedName> importRoots = assembleImportRoots(target);
      for (ArtifactLocation source : getPySources(target)) {
        List<QualifiedName> sourceImports = assembleSourceImportsFromImportRoots(importRoots,
            toImportString(source));
        for (QualifiedName sourceImport : sourceImports) {
          if (null != sourceImport.getLastComponent()) {
            shortNames.put(sourceImport.getLastComponent(), sourceImport);
            PsiElementProvider psiProvider = artifactSupplierToPsiElementProviderMapper
                .map(project, decoder, source);
            map.put(sourceImport, psiProvider);
            if (includeParentDirectory(source)) {
              map.put(sourceImport.removeTail(1), PsiElementProvider.getParent(psiProvider));
            }
          }
        }
      }
    }
    return new PySourcesIndex(shortNames.build(), ImmutableMap.copyOf(map));
  }

  private static Collection<ArtifactLocation> getPySources(TargetIdeInfo target) {
    if (target.getPyIdeInfo() != null) {
      return target.getPyIdeInfo().getSources();
    }
    if (target.getKind().hasLanguage(LanguageClass.PYTHON)) {
      return target.getSources();
    }
    return ImmutableList.of();
  }

  /**
   * Maps a blaze artifact to the import string used to reference it.
   */
  @Nullable
  abstract QualifiedName toImportString(ArtifactLocation source);

  private static boolean includeParentDirectory(ArtifactLocation source) {
    return source.getRelativePath().endsWith(".py");
  }

  static QualifiedName fromRelativePath(String relativePath) {
    relativePath = StringUtil.trimEnd(relativePath, File.separator + PyNames.INIT_DOT_PY);
    relativePath = StringUtil.trimExtensions(relativePath);
    return QualifiedName.fromComponents(StringUtil.split(relativePath, File.separator));
  }

  /**
   * <p>
   * Introspects the target and extracts any imports as {@link QualifiedName} objects. The imports
   * are relative to the basedir of the `BUILD` file and so the logic will adjust the imports to be
   * from that directory. Later the file-paths related to the Bazel target are converted to
   * {@link QualifiedName}s as well. By looking for the imports' {@link QualifiedName} as prefix on
   * the source files' fully formed {@link QualifiedName}s, it is possible to derive how the Python
   * interpreter would experience the modules and therefore reflect this in the mapping from the
   * module names to the sources.
   * </p>
   * <p>
   * An example; Consider a `py_library` target `:mylib` that is defined in a
   * <code>BUILD.bazel</code> file;
   * </p>
   *
   * <pre>{@code
   * a/
   *  b/
   *   c/
   *     BUILD.bazel
   *     d/
   *       e.py
   * }</pre>
   *
   * <p>
   * The <code>py_library</code> might have an <code>imports</code> attribute of <code>.</code>
   * and in consideration of the <code>BUILD.bazel</code> path <code>a/b/c/BUILD.bazel</code>, this
   * means that the <code>imports</code> {@link QualifiedName} will have components
   * <code>a,b,c</code>. The logic at {@link #buildSourcesIndex(Project, BlazeProjectData)} will
   * consider file <code>a/b/c/d/e.py</code> which will convert to a {@link QualifiedName} with
   * components <code>a,b,c,d,e</code> and by removing the prefix obtained from
   * <code>imports</code>, the final module {@link QualifiedName} will have components
   * <code>d,e</code>.
   * </p>
   */
  private static List<QualifiedName> assembleImportRoots(TargetIdeInfo target) {
    ArtifactLocation buildFileArtifactLocation = target.getBuildFile();

    if (null == buildFileArtifactLocation) {
      return ImmutableList.of();
    }

    PyIdeInfo ideInfo = target.getPyIdeInfo();

    if (null == ideInfo) {
      return ImmutableList.of();
    }

    Path buildPath = Path.of(target.getBuildFile().getExecutionRootRelativePath());
    Path buildParentPath = buildPath.getParent();

    // In the case of an external repo the build path could be `/BUILD`
    // which is problematic because the basedir is `/` which makes sense
    // in the context of a Bazel repo but not in the context of the overall
    // file-system. For this reason, the special case of `/BUILD` is taken
    // to have a basedir of `.`.

    if (null == buildParentPath || 0 == buildParentPath.getNameCount()) {
      buildParentPath = PATH_CURRENT_DIR;
    }

    ImmutableList.Builder<QualifiedName> resultBuilder = ImmutableList.builder();

    for (String imp : ideInfo.getImports()) {
      Path impPath = buildParentPath.resolve(imp).normalize();
      String[] impPathParts = new String[impPath.getNameCount()];

      for (int i = impPath.getNameCount() - 1; i >= 0; i--) {
        impPathParts[i] = impPath.getName(i).toString();
      }

      resultBuilder.add(QualifiedName.fromComponents(impPathParts));
    }

    return resultBuilder.build();
  }

  /**
   * For each of the <code>importRoots</code>, see if it matches as a prefix on the
   * <code>sourceImport</code> and then trim off the prefix; yielding the true module name.
   * Also include the original in the list as well because this still seems to be able to be
   * used as well. See {@link #assembleImportRoots(TargetIdeInfo)} for additional background.
   */
  private static List<QualifiedName> assembleSourceImportsFromImportRoots(
      List<QualifiedName> importRoots,
      QualifiedName sourceImport) {
    if (null == sourceImport || null == sourceImport.getLastComponent()) {
      return ImmutableList.of();
    }

    ImmutableList.Builder<QualifiedName> result = ImmutableList.builder();

    result.add(sourceImport);

    for (QualifiedName importsName : importRoots) {
      // if the import name equals the name this is a strange situation.
      if (!importsName.equals(sourceImport) && sourceImport.matchesPrefix(importsName)) {
        result.add(sourceImport.subQualifiedName(importsName.getComponentCount(),
            sourceImport.getComponentCount()));
      }
    }

    return result.build();
  }

  /**
   * This interface is used to isolate out the calls to statics so that unit testing is possible
   * on this class.
   */
  interface ArtifactSupplierToPsiElementProviderMapper {

    PsiElementProvider map(Project project, ArtifactLocationDecoder decoder,
        ArtifactLocation source);
  }

  private static class DefaultArtifactSupplierToPsiElementProviderMapper implements
      ArtifactSupplierToPsiElementProviderMapper {

    @Override
    public PsiElementProvider map(Project project, ArtifactLocationDecoder decoder,
        ArtifactLocation source) {
      return (manager) -> {
        File file = OutputArtifactResolver.resolve(project, decoder, source);
        if (file == null) {
          return null;
        }
        if (PyNames.INIT_DOT_PY.equals(file.getName())) {
          file = file.getParentFile();
        }
        return BlazePyResolverUtils.resolveFile(manager, file);
      };
    }
  }

}
