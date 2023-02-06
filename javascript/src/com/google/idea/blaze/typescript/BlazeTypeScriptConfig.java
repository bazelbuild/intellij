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
package com.google.idea.blaze.typescript;

import com.google.common.base.Ascii;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.io.InputStreamProvider;
import com.google.idea.blaze.base.io.VfsUtils;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.sdkcompat.javascript.JSModuleResolutionWrapper;
import com.google.idea.sdkcompat.javascript.JSModuleTargetWrapper;
import com.google.idea.sdkcompat.javascript.TypeScriptConfigAdapter;
import com.intellij.lang.javascript.frameworks.modules.JSModulePathSubstitution;
import com.intellij.lang.javascript.library.JSLibraryUtil;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfig;
import com.intellij.lang.typescript.tsconfig.TypeScriptFileImportsResolver;
import com.intellij.lang.typescript.tsconfig.TypeScriptImportConfigResolveContextImpl;
import com.intellij.lang.typescript.tsconfig.TypeScriptImportResolveContext;
import com.intellij.lang.typescript.tsconfig.TypeScriptImportsResolverProvider;
import com.intellij.lang.typescript.tsconfig.checkers.TypeScriptConfigFilesInclude;
import com.intellij.lang.typescript.tsconfig.checkers.TypeScriptConfigIncludeBase;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * {@link TypeScriptConfig} with special handling for tsconfig_editor.json and tsconfig.runfiles.
 *
 * <p>Since we know what tsconfig_editor.json can contain, we only need to worry about the
 * intersection of options that can be present in tsconfig_editor.json and the options that {@link
 * TypeScriptConfig} cares about.
 *
 * <p>Resolves all the symlinks under tsconfig.runfiles, and adds all of their roots to the paths
 * substitutions.
 */
class BlazeTypeScriptConfig extends TypeScriptConfigAdapter {
  private static final Logger logger = Logger.getInstance(BlazeTypeScriptConfig.class);

  private final Project project;
  private final Label label;
  private final VirtualFile configFile;
  private final String workspaceRelativePathPrefix;
  private final String workspaceRelativePathReplacement;

  private final NotNullLazyValue<ImmutableList<VirtualFile>> dependencies;
  private final NotNullLazyValue<TypeScriptConfigIncludeBase> includeChecker;
  private final NotNullLazyValue<TypeScriptImportResolveContext> resolveContext;
  private final NotNullLazyValue<TypeScriptFileImportsResolver> importResolver;

  // tsconfig.json default values
  private boolean compileOnSave = false;
  private JsonObject compilerOptions;
  // begin compilerOptions
  private String baseUrl = ".";
  private final NullableLazyValue<VirtualFile> baseUrlFile;
  private boolean inlineSourceMap = true;
  private String jsxFactory = "React.createElement";
  private JSModuleTargetWrapper module = JSModuleTargetWrapper.COMMON_JS;
  private JSModuleResolutionWrapper moduleResolution = JSModuleResolutionWrapper.NODE;
  private boolean noImplicitAny = true;
  private boolean noImplicitThis = true;
  private boolean noLib = true;
  private final List<JSModulePathSubstitution> paths = new ArrayList<>();
  private final List<String> plugins = new ArrayList<>();
  private final List<String> rootDirs = new ArrayList<>();
  private final NotNullLazyValue<ImmutableList<VirtualFile>> rootDirsFiles;
  private final NotNullLazyValue<List<PsiFileSystemItem>> rootDirsPsiElements;
  private boolean sourceMap = false;
  private boolean strictNullChecks = true;
  private LanguageTarget target = LanguageTarget.ES5;
  private final List<String> types = new ArrayList<>();
  // end compilerOptions
  private final List<String> filesStrings = new ArrayList<>();
  private final NotNullLazyValue<List<VirtualFile>> files;

  @Nullable
  static TypeScriptConfig getInstance(Project project, Label label, File tsconfig) {
    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);

    // as seen by the project
    VirtualFile configFile = VfsUtils.resolveVirtualFile(tsconfig, /* refreshIfNeeded= */ false);
    if (configFile == null) {
      return null;
    }

    File tsconfigEditor;
    try {
      JsonObject object =
          new JsonParser()
              .parse(
                  new InputStreamReader(
                      InputStreamProvider.getInstance().forFile(tsconfig), Charsets.UTF_8))
              .getAsJsonObject();
      tsconfigEditor =
          FileOperationProvider.getInstance()
              .getCanonicalFile(
                  new File(tsconfig.getParentFile(), object.get("extends").getAsString()));
    } catch (IOException e) {
      logger.warn(e);
      return null;
    }

    // When a path in the tsconfig_editor refers to a file in the workspace, they'll have this
    // prefix. This assumes that blaze-bin is just a subdirectory in the workspace root.
    String workspacePrefix = buildWorkspacePrefix(label.blazePackage().relativePath());

    // We must use this prefix instead after resolving the location of the tsconfig_editor. In this
    // case blaze-bin is a completely unrelated directory to the workspace root. This prefix will ..
    // all the way to the system root directory, then follow the absolute path to the workspace.
    String workspaceRelativePath =
        tsconfigEditor
            .getParentFile()
            .toPath()
            .relativize(workspaceRoot.directory().toPath())
            .toString();

    return FileOperationProvider.getInstance().exists(tsconfigEditor)
        ? new BlazeTypeScriptConfig(
            project, label, configFile, tsconfigEditor, workspacePrefix, workspaceRelativePath)
        : null;
  }

  /**
   * This is the prefix used by paths in the tsconfig to refer to files in the workspace.
   *
   * <p>E.g., the tsconfig file located in
   *
   * <pre>blaze-bin/foo/bar/tsconfig_editor.json</pre>
   *
   * referring to the workspace file
   *
   * <pre>foo/bar/foo.ts</pre>
   *
   * would look like
   *
   * <pre>../../../foo/bar/foo.ts</pre>
   *
   * One set of ".." for each component in the blaze package plus one for blaze-bin directory at the
   * workspace root.
   */
  private static String buildWorkspacePrefix(String blazePackage) {
    if (blazePackage.isEmpty()) {
      return "..";
    }
    return Stream.concat(
            Stream.of(".."),
            Splitter.on('/').splitToList(blazePackage).stream().map(component -> ".."))
        .collect(Collectors.joining("/"));
  }

  private BlazeTypeScriptConfig(
      Project project,
      Label label,
      VirtualFile configFile,
      File tsconfigEditor,
      String workspaceRelativePathPrefix,
      String workspaceRelativePathReplacement) {
    this.project = project;
    this.label = label;
    this.configFile = configFile;
    this.workspaceRelativePathPrefix = workspaceRelativePathPrefix;
    this.workspaceRelativePathReplacement = workspaceRelativePathReplacement;

    this.baseUrlFile =
        NullableLazyValue.createValue(
            () ->
                VfsUtils.resolveVirtualFile(
                    new File(tsconfigEditor.getParentFile(), baseUrl),
                    /* refreshIfNeeded= */ false));
    this.rootDirsFiles =
        NotNullLazyValue.createValue(
            () ->
                baseUrlFile.getValue() != null
                    ? rootDirs.stream()
                        .map(baseUrlFile.getValue()::findFileByRelativePath)
                        .filter(Objects::nonNull)
                        .collect(ImmutableList.toImmutableList())
                    : ImmutableList.of());
    this.rootDirsPsiElements =
        NotNullLazyValue.createValue(
            () -> {
              PsiManager psiManager = PsiManager.getInstance(project);
              return rootDirsFiles.getValue().stream()
                  .map(psiManager::findDirectory)
                  .filter(Objects::nonNull)
                  .collect(ImmutableList.toImmutableList());
            });
    this.files = NotNullLazyValue.createValue(this::resolveFilesList);
    this.dependencies =
        NotNullLazyValue.createValue(
            () -> {
              VirtualFile file =
                  VfsUtils.resolveVirtualFile(tsconfigEditor, /* refreshIfNeeded= */ false);
              return file != null ? ImmutableList.of(file) : ImmutableList.of();
            });
    this.includeChecker =
        NotNullLazyValue.createValue(() -> new TypeScriptConfigFilesInclude(this));
    this.resolveContext =
        NotNullLazyValue.createValue(() -> new TypeScriptImportConfigResolveContextImpl(this));
    this.importResolver =
        NotNullLazyValue.createValue(
            () -> TypeScriptImportsResolverProvider.getResolver(project, this));
    initImportsStructure(project);

    try {
      parseJson(
          new JsonParser()
              .parse(
                  new InputStreamReader(
                      InputStreamProvider.getInstance().forFile(tsconfigEditor), Charsets.UTF_8))
              .getAsJsonObject());
    } catch (IOException e) {
      logger.warn(e);
    }
  }

  private void parseJson(JsonObject json) {
    for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
      String name = entry.getKey();
      JsonElement value = entry.getValue();
      switch (name) {
        case "compileOnSave":
          this.compileOnSave = value.getAsBoolean();
          break;
        case "compilerOptions":
          parseCompilerOptions(value.getAsJsonObject());
          break;
        case "files":
          for (JsonElement path : value.getAsJsonArray()) {
            String pathString = path.getAsString();
            {
              if (pathString.startsWith(workspaceRelativePathPrefix)) {
                pathString =
                    workspaceRelativePathReplacement
                        + pathString.substring(workspaceRelativePathPrefix.length());
              }
            }
            this.filesStrings.add(pathString);
          }
          break;
        default:
          // ignored
      }
    }
  }

  private void parseCompilerOptions(JsonObject json) {
    this.compilerOptions = json;
    for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
      String name = entry.getKey();
      JsonElement value = entry.getValue();
      switch (name) {
        case "baseUrl":
          this.baseUrl = value.getAsString();
          break;
        case "inlineSourceMap":
          this.inlineSourceMap = value.getAsBoolean();
          break;
        case "jsxFactory":
          this.jsxFactory = value.getAsString();
          break;
        case "module":
          switch (Ascii.toLowerCase(value.getAsString())) {
            case "commonjs":
              this.module = JSModuleTargetWrapper.COMMON_JS;
              break;
            case "other":
              this.module = JSModuleTargetWrapper.OTHER;
              break;
            default:
              this.module = JSModuleTargetWrapper.UNKNOWN;
              break;
          }
          break;
        case "moduleResolution":
          switch (Ascii.toLowerCase(value.getAsString())) {
            case "node":
              this.moduleResolution = JSModuleResolutionWrapper.NODE;
              break;
            case "classic":
              this.moduleResolution = JSModuleResolutionWrapper.CLASSIC;
              break;
            default:
              this.moduleResolution = JSModuleResolutionWrapper.UNKNOWN;
              break;
          }
          break;
        case "noImplicitAny":
          this.noImplicitAny = value.getAsBoolean();
          break;
        case "noImplicitThis":
          this.noImplicitThis = value.getAsBoolean();
          break;
        case "noLib":
          this.noLib = value.getAsBoolean();
          break;
        case "paths":
          parsePaths(value.getAsJsonObject());
          break;
        case "plugins":
          for (JsonElement plugin : value.getAsJsonArray()) {
            plugins.add(plugin.getAsJsonObject().get("name").getAsString());
          }
          break;
        case "rootDirs":
          for (JsonElement rootDir : value.getAsJsonArray()) {
            String rootDirString = rootDir.getAsString();
            if (rootDirString.startsWith(workspaceRelativePathPrefix)) {
              rootDirString =
                  workspaceRelativePathReplacement
                      + rootDirString.substring(workspaceRelativePathPrefix.length());
            }
            rootDirs.add(rootDirString);
          }
          break;
        case "sourceMap":
          this.sourceMap = value.getAsBoolean();
          break;
        case "strictNullChecks":
          this.strictNullChecks = value.getAsBoolean();
          break;
        case "target":
          switch (Ascii.toLowerCase(value.getAsString())) {
            case "esnext":
              this.target = LanguageTarget.NEXT;
              break;
            case "es3":
              this.target = LanguageTarget.ES3;
              break;
            case "es5":
              this.target = LanguageTarget.ES5;
              break;
            case "es6":
            case "es2015":
              this.target = LanguageTarget.ES6;
              break;
            default:
              // ignored, assume es5
          }
          break;
        case "types":
          for (JsonElement type : value.getAsJsonArray()) {
            types.add(type.getAsString());
          }
          break;
        default:
          // ignored
      }
    }
  }

  private void parsePaths(JsonObject json) {
    String runfilesPrefix = null;
    List<String> alternativePrefixes = new ArrayList<>();
    VirtualFile base = baseUrlFile.getValue();
    if (base != null) {
      Path baseUrlPath = VfsUtil.virtualToIoFile(base).toPath();
      BuildSystemName buildSystem = Blaze.getBuildSystemName(project);
      File workspaceRoot = WorkspaceRoot.fromProject(project).directory();
      File blazeBin = new File(workspaceRoot, BlazeInfo.blazeBinKey(buildSystem));
      File blazeGenfiles = new File(workspaceRoot, BlazeInfo.blazeGenfilesKey(buildSystem));

      // modules are resolved in this order
      alternativePrefixes.add(baseUrlPath.relativize(workspaceRoot.toPath()).toString());
      alternativePrefixes.add(baseUrlPath.relativize(blazeBin.toPath()).toString());
      alternativePrefixes.add(baseUrlPath.relativize(blazeGenfiles.toPath()).toString());

      FileOperationProvider fOps = FileOperationProvider.getInstance();
      if (fOps.isSymbolicLink(blazeBin)) {
        try {
          alternativePrefixes.add(
              baseUrlPath.relativize(fOps.readSymbolicLink(blazeBin).toPath()).toString());
        } catch (IOException e) {
          logger.warn(e);
        }
      }
      if (fOps.isSymbolicLink(blazeGenfiles)) {
        try {
          alternativePrefixes.add(
              baseUrlPath.relativize(fOps.readSymbolicLink(blazeGenfiles).toPath()).toString());
        } catch (IOException e) {
          logger.warn(e);
        }
      }
      runfilesPrefix = "./" + label.targetName() + ".runfiles/" + workspaceRoot.getName();
    }

    for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
      String name = entry.getKey();
      List<String> mappings = new ArrayList<>();
      for (JsonElement path : entry.getValue().getAsJsonArray()) {
        String pathString = path.getAsString();
        if (pathString.startsWith(workspaceRelativePathPrefix)) {
          pathString =
              workspaceRelativePathReplacement
                  + pathString.substring(workspaceRelativePathPrefix.length());
        }
        mappings.add(pathString);
      }
      paths.add(new PathSubstitution(name, mappings, alternativePrefixes, runfilesPrefix));
    }
  }

  private ImmutableList<VirtualFile> resolveFilesList() {
    VirtualFile base = baseUrlFile.getValue();
    if (base == null) {
      return ImmutableList.of();
    }
    File baseFile = VfsUtil.virtualToIoFile(base);
    FileOperationProvider fOps = FileOperationProvider.getInstance();
    return filesStrings.stream()
        .map(f -> new File(baseFile, f))
        .map(
            f -> {
              try {
                return fOps.isSymbolicLink(f) ? fOps.readSymbolicLink(f) : f;
              } catch (IOException e) {
                logger.warn(e);
                return f;
              }
            })
        .map(f -> VfsUtils.resolveVirtualFile(f, /* refreshIfNeeded= */ false))
        .filter(Objects::nonNull)
        .collect(ImmutableList.toImmutableList());
  }

  @Override
  public boolean isDirectoryBased() {
    return false;
  }

  @Override
  public VirtualFile getConfigFile() {
    return configFile;
  }

  @Override
  public VirtualFile getConfigDirectory() {
    return configFile.getParent();
  }

  @Override
  public Collection<JSModulePathSubstitution> getPaths() {
    return paths;
  }

  @Override
  public Collection<PsiFileSystemItem> getRootDirs() {
    return rootDirsPsiElements.getValue();
  }

  @Override
  public LanguageTarget getLanguageTarget() {
    return target;
  }

  @Nullable
  @Override
  public VirtualFile getBaseUrl() {
    return baseUrlFile.getValue();
  }

  @Nullable
  @Override
  public String getOutDirectory() {
    return null;
  }

  @Override
  public boolean hasErrors() {
    return false;
  }

  @Override
  public boolean isInlineSourceMap() {
    return inlineSourceMap;
  }

  @Override
  public boolean isSourceMap() {
    return sourceMap;
  }

  @Override
  public Collection<String> getLibNames() {
    return ImmutableList.of();
  }

  @Override
  public Collection<VirtualFile> getTypeRoots() {
    return ImmutableList.of();
  }

  @Override
  public @NotNull JSModuleResolutionWrapper getAdapterResolution() {
    return moduleResolution;
  }

  @Override
  public JSModuleResolutionWrapper getAdapterEffectiveResolution() {
    return moduleResolution;
  }

  @Override
  public Collection<String> getTypes() {
    return types;
  }

  @Override
  public JSModuleTargetWrapper getAdapterModule() {
    return module;
  }

  @Override
  public boolean isIncludedFile(VirtualFile file, boolean checkExclude) {
    return false;
  }

  @Override
  public boolean isFromFileList(VirtualFile file) {
    return getInclude().isFromFilesList(file);
  }

  @Nullable
  @Override
  public String getRawCompilerOption(String name) {
    if (!compilerOptions.has(name)) {
      return null;
    }
    JsonElement option = compilerOptions.get(name);
    return option.isJsonPrimitive() ? option.getAsString() : option.toString();
  }

  @Override
  public boolean hasExplicitCompileOnSave() {
    return true;
  }

  @Override
  public Collection<VirtualFile> getRootDirsFiles() {
    return rootDirsFiles.getValue();
  }

  @Override
  public Collection<String> getExcludePatterns() {
    return JSLibraryUtil.LIBRARY_ROOT_DIR_NAME_SET;
  }

  @Override
  public Collection<String> getIncludePatterns() {
    return ImmutableList.of();
  }

  /**
   * This is still useful for prefetching and adding symlink-resolved library files to the project.
   */
  @Override
  public Collection<VirtualFile> getFileList() {
    return files.getValue();
  }

  /**
   * https://www.typescriptlang.org/docs/handbook/tsconfig-json.html
   *
   * <p>If the "files" and "include" are both left unspecified, the compiler defaults to including
   * all TypeScript (.ts, .d.ts and .tsx) files in the containing directory and subdirectories
   * except those excluded using the "exclude" property.
   */
  @Override
  public boolean hasFilesList() {
    return false;
  }

  @Override
  public boolean hasIncludesList() {
    return false;
  }

  @Override
  public Collection<VirtualFile> getDependencies() {
    return dependencies.getValue();
  }

  @Override
  public TypeScriptConfigIncludeBase getInclude() {
    return includeChecker.getValue();
  }

  @Override
  public TypeScriptImportResolveContext getResolveContext() {
    return resolveContext.getValue();
  }

  @Override
  public boolean allowJs() {
    return false;
  }

  @Override
  public boolean suppressExcessPropertyChecks() {
    return false;
  }

  @Override
  public boolean checkJs() {
    return false;
  }

  @Override
  public boolean noImplicitAny() {
    return noImplicitAny;
  }

  @Override
  public boolean noImplicitThis() {
    return noImplicitThis;
  }

  @Override
  public boolean strictNullChecks() {
    return strictNullChecks;
  }

  @Override
  public boolean hasCompilerOption(String name) {
    return compilerOptions.has(name);
  }

  @Override
  public boolean strictBindCallApply() {
    return false;
  }

  @Override
  public boolean allowSyntheticDefaultImports() {
    return false;
  }

  @Override
  public boolean noLib() {
    return noLib;
  }

  @Nullable
  @Override
  public VirtualFile getRootDirFile() {
    return null;
  }

  @Override
  public boolean preserveSymlinks() {
    return false;
  }

  @Nullable
  @Override
  public String jsxFactory() {
    return jsxFactory;
  }

  @Override
  public TypeScriptFileImportsResolver getImportResolver() {
    return importResolver.getValue();
  }

  @Override
  public List<String> getPlugins() {
    return plugins;
  }

  @Override
  public boolean keyofStringsOnly() {
    return false;
  }

  static class PathSubstitution implements JSModulePathSubstitution {
    private final String pattern;
    private final ImmutableList<String> mappings;

    PathSubstitution(
        String pattern,
        List<String> mappings,
        List<String> alternativePrefixes,
        @Nullable String runfilesPrefix) {
      this.pattern = pattern;
      List<String> candidates = new ArrayList<>();
      for (String mapping : mappings) {
        if (runfilesPrefix != null && mapping.startsWith(runfilesPrefix)) {
          for (String alternativeRoot : alternativePrefixes) {
            candidates.add(alternativeRoot + mapping.substring(runfilesPrefix.length()));
          }
        }
      }
      // fall back to the runfiles if no other path resolves
      candidates.addAll(mappings);
      this.mappings = candidates.stream().distinct().collect(ImmutableList.toImmutableList());
    }

    @Override
    public Collection<String> getMappings() {
      return mappings;
    }

    @Override
    public String getPattern() {
      return pattern;
    }

    @Override
    public boolean canStartWith() {
      return false;
    }
  }
}
