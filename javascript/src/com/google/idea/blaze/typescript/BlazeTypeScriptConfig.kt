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
package com.google.idea.blaze.typescript

import com.google.common.base.Ascii
import com.google.common.base.Splitter
import com.google.common.collect.ImmutableList
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.idea.blaze.base.command.info.BlazeInfo
import com.google.idea.blaze.base.io.FileOperationProvider
import com.google.idea.blaze.base.io.InputStreamProvider
import com.google.idea.blaze.base.io.VfsUtils
import com.google.idea.blaze.base.model.primitives.Label
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot
import com.google.idea.blaze.base.settings.Blaze
import com.google.idea.blaze.base.settings.BuildSystemName
import com.google.idea.sdkcompat.javascript.TypeScriptConfigAdapter
import com.intellij.lang.javascript.config.JSFileImports
import com.intellij.lang.javascript.config.JSFileImportsImpl
import com.intellij.lang.javascript.config.JSModuleResolution
import com.intellij.lang.javascript.config.JSModuleTarget
import com.intellij.lang.javascript.frameworks.modules.JSModuleMapping
import com.intellij.lang.javascript.frameworks.modules.JSModulePathMappings
import com.intellij.lang.javascript.frameworks.modules.JSModulePathSubstitution
import com.intellij.lang.javascript.library.JSLibraryUtil
import com.intellij.lang.typescript.tsconfig.TypeScriptConfig
import com.intellij.lang.typescript.tsconfig.TypeScriptConfig.LanguageTarget
import com.intellij.lang.typescript.tsconfig.TypeScriptFileImportsResolver
import com.intellij.lang.typescript.tsconfig.TypeScriptImportConfigResolveContextImpl
import com.intellij.lang.typescript.tsconfig.TypeScriptImportResolveContext
import com.intellij.lang.typescript.tsconfig.TypeScriptImportsResolverProvider
import com.intellij.lang.typescript.tsconfig.checkers.TypeScriptConfigFilesInclude
import com.intellij.lang.typescript.tsconfig.checkers.TypeScriptConfigIncludeBase
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.openapi.util.NullableLazyValue
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import org.jetbrains.annotations.NotNull
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import javax.annotation.Nullable

/**
 * [TypeScriptConfig] with special handling for tsconfig_editor.json and tsconfig.runfiles.
 *
 * Resolves all the symlinks under tsconfig.runfiles, and adds all of their roots to the path
 * substitutions.
 */
class BlazeTypeScriptConfig private constructor(
  private val project: Project,
  private val label: Label,
  private val configFile: VirtualFile,
  tsconfigEditor: File,
  private val workspaceRelativePathPrefix: String,
  private val workspaceRelativePathReplacement: String
) : TypeScriptConfigAdapter() {

  private val logger = Logger.getInstance(BlazeTypeScriptConfig::class.java)

  // tsconfig.json defaults
  private var compileOnSave: Boolean = false
  private lateinit var compilerOptions: JsonObject

  // compilerOptions
  private var baseUrl: String = "."
  private val baseUrlFile: NullableLazyValue<VirtualFile?> =
    NullableLazyValue.createValue<VirtualFile?> {
      VfsUtils.resolveVirtualFile(File(tsconfigEditor.parentFile, baseUrl), false)
    }

  private var inlineSourceMap: Boolean = true
  private var jsxFactory: String = "React.createElement"
  private var module: JSModuleTarget = JSModuleTarget.COMMON_JS
  private var moduleResolution: JSModuleResolution = JSModuleResolution.NODE
  private var noImplicitAny: Boolean = true
  private var noImplicitThis: Boolean = true
  private var noLib: Boolean = true
  private val paths: MutableList<JSModulePathSubstitution> = mutableListOf()
  private val plugins: MutableList<String> = mutableListOf()
  private val rootDirs: MutableList<String> = mutableListOf()
  private val rootDirsFiles: NotNullLazyValue<ImmutableList<VirtualFile>> =
    NotNullLazyValue.createValue {
      val base = baseUrlFile.value
      if (base != null)
        rootDirs.mapNotNull { base.findFileByRelativePath(it) }.let { ImmutableList.copyOf(it) }
      else ImmutableList.of()
    }

  private val rootDirsPsiElements: NotNullLazyValue<List<PsiFileSystemItem>> =
    NotNullLazyValue.createValue {
      val psi = PsiManager.getInstance(project)
      rootDirsFiles.value.mapNotNull { psi.findDirectory(it) } as List<PsiFileSystemItem>
    }

  private var sourceMap: Boolean = false
  private var strictNullChecks: Boolean = true
  private var target: LanguageTarget = LanguageTarget.ES5
  private val types: MutableList<String> = mutableListOf()
  // end compilerOptions

  private val filesStrings: MutableList<String> = mutableListOf()
  private val files: NotNullLazyValue<ImmutableList<VirtualFile>> =
    NotNullLazyValue.createValue { resolveFilesList() }

  private val dependencies: NotNullLazyValue<ImmutableList<VirtualFile>> =
    NotNullLazyValue.createValue {
      VfsUtils.resolveVirtualFile(tsconfigEditor, false)?.let { ImmutableList.of(it) } ?: ImmutableList.of()
    }

  private val includeChecker: NotNullLazyValue<TypeScriptConfigIncludeBase> =
    NotNullLazyValue.createValue { TypeScriptConfigFilesInclude(this) }

  private val resolveContext: NotNullLazyValue<TypeScriptImportResolveContext> =
    NotNullLazyValue.createValue { TypeScriptImportConfigResolveContextImpl(this) }

  private val importResolver: NotNullLazyValue<TypeScriptFileImportsResolver> =
    NotNullLazyValue.createValue { TypeScriptImportsResolverProvider.getResolver(project, this) }

  private val importStructure: NotNullLazyValue<JSFileImports> =
    NotNullLazyValue.createValue { JSFileImportsImpl(project, this) }

  init {
    try {
      val json = JsonParser.parseReader(
        InputStreamReader(InputStreamProvider.getInstance().forFile(tsconfigEditor), StandardCharsets.UTF_8)
      ).asJsonObject
      parseJson(json)
    } catch (e: IOException) {
      logger.warn(e)
    }
  }

  // region Parsing

  private fun parseJson(json: JsonObject) {
    for ((name, value) in json.entrySet()) {
      when (name) {
        "compileOnSave" -> compileOnSave = value.asBoolean
        "compilerOptions" -> parseCompilerOptions(value.asJsonObject)
        "files" -> {
          value.asJsonArray.forEach { elem ->
            var pathString = elem.asString
            if (pathString.startsWith(workspaceRelativePathPrefix)) {
              pathString = workspaceRelativePathReplacement + pathString.substring(workspaceRelativePathPrefix.length)
            }
            filesStrings += pathString
          }
        }
        else -> { /* ignored */ }
      }
    }
  }

  private fun parseCompilerOptions(json: JsonObject) {
    compilerOptions = json
    for ((name, value) in json.entrySet()) {
      when (name) {
        "baseUrl" -> baseUrl = value.asString
        "inlineSourceMap" -> inlineSourceMap = value.asBoolean
        "jsxFactory" -> jsxFactory = value.asString

        "module" -> {
          module = when (Ascii.toLowerCase(value.asString)) {
            "commonjs" -> JSModuleTarget.COMMON_JS
            "other" -> JSModuleTarget.OTHER
            else -> JSModuleTarget.UNKNOWN
          }
        }

        "moduleResolution" -> {
          moduleResolution = when (Ascii.toLowerCase(value.asString)) {
            "node" -> JSModuleResolution.NODE
            "classic" -> JSModuleResolution.CLASSIC
            else -> JSModuleResolution.UNKNOWN
          }
        }

        "noImplicitAny" -> noImplicitAny = value.asBoolean
        "noImplicitThis" -> noImplicitThis = value.asBoolean
        "noLib" -> noLib = value.asBoolean

        "paths" -> parsePaths(value.asJsonObject)

        "plugins" -> value.asJsonArray.forEach { plugins += it.asJsonObject["name"].asString }

        "rootDirs" -> value.asJsonArray.forEach {
          var rootDir = it.asString
          if (rootDir.startsWith(workspaceRelativePathPrefix)) {
            rootDir = workspaceRelativePathReplacement + rootDir.substring(workspaceRelativePathPrefix.length)
          }
          rootDirs += rootDir
        }

        "sourceMap" -> sourceMap = value.asBoolean
        "strictNullChecks" -> strictNullChecks = value.asBoolean

        "target" -> {
          target = when (Ascii.toLowerCase(value.asString)) {
            "esnext" -> LanguageTarget.NEXT
            "es3" -> LanguageTarget.ES3
            "es5" -> LanguageTarget.ES5
            "es6", "es2015" -> LanguageTarget.ES6
            else -> LanguageTarget.ES5
          }
        }

        "types" -> value.asJsonArray.forEach { types += it.asString }

        else -> { /* ignored */ }
      }
    }
  }

  private fun parsePaths(json: JsonObject) {
    var runfilesPrefix: String? = null
    val alternativePrefixes = mutableListOf<String>()
    val base = baseUrlFile.value
    if (base != null) {
      val baseUrlPath: Path = VfsUtil.virtualToIoFile(base).toPath()
      val buildSystem: BuildSystemName = Blaze.getBuildSystemName(project)
      val workspaceRoot: File = WorkspaceRoot.fromProject(project).directory()
      val blazeBin = File(workspaceRoot, BlazeInfo.blazeBinKey(buildSystem))
      val blazeGenfiles = File(workspaceRoot, BlazeInfo.blazeGenfilesKey(buildSystem))

      // resolution order
      alternativePrefixes += baseUrlPath.relativize(workspaceRoot.toPath()).toString()
      alternativePrefixes += baseUrlPath.relativize(blazeBin.toPath()).toString()
      alternativePrefixes += baseUrlPath.relativize(blazeGenfiles.toPath()).toString()

      val fOps = FileOperationProvider.getInstance()
      if (fOps.isSymbolicLink(blazeBin)) {
        try {
          alternativePrefixes += baseUrlPath.relativize(fOps.readSymbolicLink(blazeBin).toPath()).toString()
        } catch (e: IOException) {
          logger.warn(e)
        }
      }
      if (fOps.isSymbolicLink(blazeGenfiles)) {
        try {
          alternativePrefixes += baseUrlPath.relativize(fOps.readSymbolicLink(blazeGenfiles).toPath()).toString()
        } catch (e: IOException) {
          logger.warn(e)
        }
      }
      runfilesPrefix = "./" + label.targetName() + ".runfiles/" + workspaceRoot.name
    }

    for ((name, jsonArr) in json.entrySet()) {
      val mappings = mutableListOf<String>()
      jsonArr.asJsonArray.forEach {
        var pathString = it.asString
        if (pathString.startsWith(workspaceRelativePathPrefix)) {
          pathString = workspaceRelativePathReplacement + pathString.substring(workspaceRelativePathPrefix.length)
        }
        mappings += pathString
      }
      paths += PathSubstitution(name, mappings, alternativePrefixes.toList(), runfilesPrefix)
    }
  }

  private fun resolveFilesList(): ImmutableList<VirtualFile> {
    val base = baseUrlFile.value ?: return ImmutableList.of()
    val baseFile = VfsUtil.virtualToIoFile(base)
    val fOps = FileOperationProvider.getInstance()
    val resolved = filesStrings.map { File(baseFile, it) }.map { f ->
      try {
        if (fOps.isSymbolicLink(f)) fOps.readSymbolicLink(f) else f
      } catch (e: IOException) {
        logger.warn(e)
        f
      }
    }.mapNotNull { f -> VfsUtils.resolveVirtualFile(f, false) }
    return ImmutableList.copyOf(resolved)
  }

  // endregion Parsing

  // region TypeScriptConfig

  override fun getConfigImportResolveStructure(): JSFileImports = importStructure.value

  override fun getPathMappings(): @NotNull JSModulePathMappings<JSModulePathSubstitution> =
    JSModulePathMappings.build(paths)

  override fun isDirectoryBased(): Boolean = false

  override fun getConfigFile(): VirtualFile = configFile

  override fun getConfigDirectory(): VirtualFile = configFile.parent

  override fun getDeclarationDir(): String? = null

  override fun getPaths(): Collection<JSModulePathSubstitution> = paths

  override fun getRootDirs(): Collection<PsiFileSystemItem> = rootDirsPsiElements.value

  override fun getLanguageTarget(): LanguageTarget = target

  override fun getBaseUrl(): VirtualFile? = baseUrlFile.value

  override fun getOutDirectory(): String? = null

  override fun hasErrors(): Boolean = false

  override fun isInlineSourceMap(): Boolean = inlineSourceMap

  override fun isSourceMap(): Boolean = sourceMap

  override fun getLibNames(): Collection<String> = ImmutableList.of()

  override fun getTypeRoots(): Collection<VirtualFile> = ImmutableList.of()

  override fun getResolution(): @NotNull JSModuleResolution = moduleResolution

  override fun getEffectiveResolution(): JSModuleResolution = moduleResolution

  override fun getTypes(): Collection<String> = types

  override fun getModule(): JSModuleTarget = module

  override fun isIncludedFile(file: VirtualFile, checkExclude: Boolean): Boolean = false

  override fun isFromFileList(file: VirtualFile): Boolean = include.includeFromFilesList(file)

  override fun getRawCompilerOption(name: String): String? {
    if (!::compilerOptions.isInitialized || !compilerOptions.has(name)) return null
    val option: JsonElement = compilerOptions[name]
    return if (option.isJsonPrimitive) option.asString else option.toString()
  }

  override fun hasExplicitCompileOnSave(): Boolean = true

  override fun compileOnSave(): Boolean = compileOnSave

  override fun getRootDirsFiles(): Collection<VirtualFile> = rootDirsFiles.value

  override fun getExcludePatterns(): Collection<String> = JSLibraryUtil.LIBRARY_ROOT_DIR_NAME_SET

  override fun getIncludePatterns(): Collection<String> = ImmutableList.of()

  /** Useful for prefetching and adding symlink-resolved library files to the project. */
  override fun getFileList(): Collection<VirtualFile> = files.value

  /**
   * If "files" and "include" are both unspecified, TS includes all .ts/.d.ts/.tsx in dir by default.
   */
  override fun hasFilesList(): Boolean = false

  override fun hasIncludesList(): Boolean = false

  override fun getDependencies(): Collection<VirtualFile> = dependencies.value

  override fun getInclude(): TypeScriptConfigIncludeBase = includeChecker.value

  override fun getResolveContext(): TypeScriptImportResolveContext = resolveContext.value

  override fun allowJs(): Boolean = false

  override fun suppressExcessPropertyChecks(): Boolean = false

  override fun checkJs(): Boolean = false

  override fun noImplicitAny(): Boolean = noImplicitAny

  override fun noImplicitThis(): Boolean = noImplicitThis

  override fun strictNullChecks(): Boolean = strictNullChecks

  override fun hasCompilerOption(name: String): Boolean =
    ::compilerOptions.isInitialized && compilerOptions.has(name)

  override fun strictBindCallApply(): Boolean = false

  override fun allowSyntheticDefaultImports(): Boolean = false

  override fun noLib(): Boolean = noLib

  override fun getRootDirFile(): VirtualFile? = null

  override fun preserveSymlinks(): Boolean = false

  override fun jsxFactory(): String? = jsxFactory

  override fun getImportResolver(): TypeScriptFileImportsResolver = importResolver.value

  override fun getPlugins(): List<String> = plugins

  override fun keyofStringsOnly(): Boolean = false

  override fun getModuleSuffixes(): @NotNull Collection<String> = emptyList()

  override fun esModuleInterop(): Boolean = false

  override fun experimentalDecorators(): Boolean = false

  override fun verbatimModuleSyntax(): Boolean = false

  override fun allowImportingTsExtensions(): Boolean = false

  override fun isComposite(): Boolean = false

  override fun getReferences(): @NotNull Collection<VirtualFile> = emptyList()

  override fun resolveJsonModule(): Boolean = false

  override fun isolatedModules(): Boolean = false

  override fun noImplicitOverride(): Boolean = false

  override fun isNodeResolution(): Boolean = moduleResolution == JSModuleResolution.NODE

  override fun isJSConfig(): Boolean = false

  override fun hasCustomOption(key: Key<*>): Boolean = false

  override fun <T : Any?> getCustomOption(key: Key<T>): T? = null

  override fun getModuleResolution(): @NotNull() JSModuleResolution = moduleResolution

  override fun getEffectiveModuleResolution(): @NotNull() JSModuleResolution = moduleResolution

  // endregion TypeScriptConfig

  // region Helpers

  private fun TypeScriptConfigIncludeBase.includeFromFilesList(file: VirtualFile): Boolean =
    isFromFilesList(file)

  // endregion Helpers

  companion object {
    private fun buildWorkspacePrefix(blazePackage: String): String {
      if (blazePackage.isEmpty()) return ".."
      val ups = Splitter.on('/').splitToList(blazePackage).map { ".." }
      return (listOf("..") + ups).joinToString("/")
    }

    @JvmStatic
    fun getInstance(project: Project, label: Label, tsconfig: File): TypeScriptConfig? {
      val workspaceRoot = WorkspaceRoot.fromProject(project)

      val configFile = VfsUtils.resolveVirtualFile(tsconfig, false) ?: return null

      val tsconfigEditor: File = try {
        val obj = JsonParser.parseReader(
          InputStreamReader(InputStreamProvider.getInstance().forFile(tsconfig), StandardCharsets.UTF_8)
        ).asJsonObject
        FileOperationProvider.getInstance().getCanonicalFile(
          File(tsconfig.parentFile, obj["extends"].asString)
        )
      } catch (e: IOException) {
        Logger.getInstance(BlazeTypeScriptConfig::class.java).warn(e)
        return null
      }

      val workspacePrefix = buildWorkspacePrefix(label.blazePackage().relativePath())

      val workspaceRelativePath =
        tsconfigEditor.parentFile.toPath().relativize(workspaceRoot.directory().toPath()).toString()

      return if (FileOperationProvider.getInstance().exists(tsconfigEditor)) {
        BlazeTypeScriptConfig(
          project,
          label,
          configFile,
          tsconfigEditor,
          workspacePrefix,
          workspaceRelativePath
        )
      } else {
        null
      }
    }
  }

  class PathSubstitution(
    private val pattern: String,
    mappings: List<String>,
    alternativePrefixes: List<String>,
    @param:Nullable private val runfilesPrefix: String?
  ) : JSModulePathSubstitution {

    private val mappings: ImmutableList<String>

    init {
      val candidates = mutableListOf<String>()
      for (mapping in mappings) {
        if (runfilesPrefix != null && mapping.startsWith(runfilesPrefix)) {
          for (alt in alternativePrefixes) {
            candidates += alt + mapping.substring(runfilesPrefix.length)
          }
        }
      }
      // fall back to the runfiles if no other path resolves
      candidates += mappings
      this.mappings = candidates.distinct().let { ImmutableList.copyOf(it) }
    }

    override fun getMappings(): Collection<JSModuleMapping> =
      mappings.mapNotNull { JSModuleMapping(it) }

    override fun getPattern(): String = pattern

    override fun canStartWith(): Boolean = false
  }
}