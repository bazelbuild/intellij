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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.io.InputStreamProvider;
import com.google.idea.blaze.base.io.VfsUtils;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BuildSystem;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.lang.javascript.frameworks.modules.JSModulePathSubstitution;
import com.intellij.lang.javascript.library.JSLibraryUtil;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfig;
import com.intellij.lang.typescript.tsconfig.TypeScriptFileImports;
import com.intellij.lang.typescript.tsconfig.TypeScriptFileImportsImpl;
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
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;

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
class BlazeTypeScriptConfig implements TypeScriptConfig {
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
  private final NotNullLazyValue<TypeScriptFileImports> importStructure;

  // tsconfig.json default values
  private boolean compileOnSave = false;
  private JsonObject compilerOptions;
  // begin compilerOptions
  private String baseUrl = ".";
  private final NullableLazyValue<VirtualFile> baseUrlFile;
  private boolean inlineSourceMap = true;
  private String jsxFactory = "React.createElement";
  private ModuleTarget module = ModuleTarget.COMMON_JS;
  private ModuleResolution moduleResolution = ModuleResolution.NODE;
  private boolean noImplicitAny = true;
  private boolean noImplicitThis = true;
  private boolean noLib = true;
  private final List<JSModulePathSubstitution> paths = new ArrayList<>();
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
  static TypeScriptConfig getInstance(Project project, Label label) {
    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);

    // as seen by the project
    VirtualFile configFile =
        VfsUtils.resolveVirtualFile(
            new File(workspaceRoot.fileForPath(label.blazePackage()), "tsconfig.json"));
    if (configFile == null) {
      return null;
    }

    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      return null;
    }

    // TODO: handle remote output artifacts, and not rely on blaze-out/blaze-bin location
    File blazeBin = projectData.getBlazeInfo().getBlazeBinDirectory();
    File tsconfigDirectory = new File(blazeBin, label.blazePackage().relativePath());
    // contains the actual content of the tsconfig
    File tsconfigEditor = new File(tsconfigDirectory, "tsconfig_editor.json");

    // need these two to replace workspace relative paths from the blaze-bin symlink in the
    // workspace root with workspace relative paths from the actual blaze-bin.
    String workspacePrefix =
        tsconfigDirectory.toPath().relativize(blazeBin.getParentFile().toPath()).toString();
    String workspaceRelativePath =
        tsconfigDirectory.toPath().relativize(workspaceRoot.directory().toPath()).toString();

    return FileOperationProvider.getInstance().exists(tsconfigEditor)
        ? new BlazeTypeScriptConfig(
            project, label, configFile, tsconfigEditor, workspacePrefix, workspaceRelativePath)
        : null;
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
            () -> VfsUtils.resolveVirtualFile(new File(tsconfigEditor.getParentFile(), baseUrl)));
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
              VirtualFile file = VfsUtils.resolveVirtualFile(tsconfigEditor);
              return file != null ? ImmutableList.of(file) : ImmutableList.of();
            });
    this.includeChecker =
        NotNullLazyValue.createValue(() -> new TypeScriptConfigFilesInclude(this));
    this.resolveContext =
        NotNullLazyValue.createValue(() -> new TypeScriptImportConfigResolveContextImpl(this));
    this.importResolver =
        NotNullLazyValue.createValue(
            () -> TypeScriptImportsResolverProvider.getResolver(project, this));
    this.importStructure =
        NotNullLazyValue.createValue(() -> new TypeScriptFileImportsImpl(project, this));

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
              this.module = ModuleTarget.COMMON_JS;
              break;
            case "other":
              this.module = ModuleTarget.OTHER;
              break;
            default:
              this.module = ModuleTarget.UNKNOWN;
              break;
          }
          break;
        case "moduleResolution":
          switch (Ascii.toLowerCase(value.getAsString())) {
            case "node":
              this.moduleResolution = ModuleResolution.NODE;
              break;
            case "classic":
              this.moduleResolution = ModuleResolution.CLASSIC;
              break;
            default:
              this.moduleResolution = ModuleResolution.UNKNOWN;
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
      BuildSystem buildSystem = Blaze.getBuildSystem(project);
      File workspaceRoot = WorkspaceRoot.fromProject(project).directory();
      File blazeBin = new File(workspaceRoot, BlazeInfo.blazeBinKey(buildSystem));
      File blazeGenfiles = new File(workspaceRoot, BlazeInfo.blazeGenfilesKey(buildSystem));

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
        .map(VfsUtils::resolveVirtualFile)
        .filter(Objects::nonNull)
        .collect(ImmutableList.toImmutableList());
  }

  @Override
  public boolean accept(VirtualFile file) {
    return getInclude().accept(file);
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
  public boolean isCompileOnSave() {
    return compileOnSave;
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
  public ModuleResolution getResolution() {
    return moduleResolution;
  }

  @Override
  public ModuleResolution getEffectiveResolution() {
    return moduleResolution;
  }

  @Override
  public Collection<String> getTypes() {
    return types;
  }

  @Override
  public ModuleTarget getModule() {
    return module;
  }

  @Override
  public boolean isIncludedFile(VirtualFile file) {
    return false;
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

  @Override
  public Collection<VirtualFile> getFileList() {
    return files.getValue();
  }

  @Override
  public boolean hasFilesList() {
    return true;
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
  public Collection<VirtualFile> getProjectReferences() {
    return ImmutableList.of();
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
  public TypeScriptFileImports getConfigImportResolveStructure() {
    return importStructure.getValue();
  }

  @Override
  public TypeScriptFileImportsResolver getImportResolver() {
    return importResolver.getValue();
  }

  static class PathSubstitution implements JSModulePathSubstitution {
    private final String pattern;
    private final ImmutableSet<String> mappings;

    PathSubstitution(
        String pattern,
        List<String> mappings,
        List<String> alternativePrefixes,
        @Nullable String runfilesPrefix) {
      this.pattern = pattern;
      ImmutableSet.Builder<String> builder = ImmutableSet.builder();
      for (String mapping : mappings) {
        builder.add(mapping);
        if (runfilesPrefix == null || !mapping.startsWith(runfilesPrefix)) {
          continue;
        }
        for (String alternativeRoot : alternativePrefixes) {
          builder.add(alternativeRoot + mapping.substring(runfilesPrefix.length()));
        }
      }
      this.mappings = builder.build();
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
