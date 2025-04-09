package com.google.idea.blaze.base.qsync

import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings
import com.google.idea.blaze.base.util.UrlUtil
import com.google.idea.blaze.common.Context
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdateOperation.JAVA_DEPS_LIB_NAME
import com.google.idea.blaze.qsync.project.ProjectPath
import com.google.idea.blaze.qsync.project.ProjectProto
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.module.ModuleTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.jps.model.java.JavaSourceRootProperties
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import java.io.File
import kotlin.text.get

object ProjectUpdaterHelper {

  private fun mapModuleType(type: ProjectProto.ModuleType): ModuleType<*> =
    when (type) {
      ProjectProto.ModuleType.MODULE_TYPE_DEFAULT -> ModuleTypeManager.getInstance().defaultModuleType
      ProjectProto.ModuleType.UNRECOGNIZED -> throw IllegalStateException("Unrecognised module type $type")
    }

  private fun getModuleForModuleSpec(
    moduleSpec: ProjectProto.Module,
    modelsProvider: IdeModifiableModelsProvider,
    imlDirectory: File,
    projectPathResolver: ProjectPath.Resolver,
    workspaceRoot: WorkspaceRoot,
    libMap: Map<String, Library>,
  ): Module {
    val module = modelsProvider.newModule(
      imlDirectory.toPath().resolve(moduleSpec.getName() + ".iml").toString(),
      mapModuleType(moduleSpec.type).id
    )
    val roots = modelsProvider.getModifiableRootModel(module)
    roots.orderEntries
      .filterIsInstance(LibraryOrderEntry::class.java)
      .forEach(roots::removeOrderEntry)

    // TODO: should this be encapsulated in ProjectProto.Module?
    roots.inheritSdk();
    // TODO instead of removing all content entries and re-adding, we should calculate the diff.
    roots.contentEntries.forEach(roots::removeContentEntry);

    for (ceSpec in moduleSpec.contentEntriesList) {
      val projectPath = ProjectPath.create(ceSpec.root)
      val contentEntry = roots.addContentEntry(
        UrlUtil.pathToUrl(
          projectPathResolver.resolve(projectPath).toString()
        )
      )
      for (sfSpec in ceSpec.sourcesList) {
        val sourceFolderProjectPath = ProjectPath.create(sfSpec.projectPath)
        val properties =
          JpsJavaExtensionService.getInstance().createSourceRootProperties(
            sfSpec.packagePrefix,
            sfSpec.isGenerated
          )
        val rootType = if (sfSpec.isTest) JavaSourceRootType.TEST_SOURCE else JavaSourceRootType.SOURCE
        val url =
          UrlUtil.pathToUrl(
            projectPathResolver.resolve(sourceFolderProjectPath).toString(),
            sourceFolderProjectPath.innerJarPath()
          )

        contentEntry.addSourceFolder<JavaSourceRootProperties?>(url, rootType, properties)
      }
      for (exclude in ceSpec.excludesList) {
        contentEntry.addExcludeFolder(
          UrlUtil.pathToIdeaDirectoryUrl(workspaceRoot.absolutePathFor(exclude))
        )
      }
    }
    if (!QuerySync.enableBazelAdditionalLibraryRootsProvider()) {
      moduleSpec.libraryNameList.forEach { lib ->
        val library = libMap[lib]
          ?: throw IllegalStateException("Module refers to library $lib not present in the project spec")
        addLibraryEntry(roots, library)
      }
    } else {
      libMap[JAVA_DEPS_LIB_NAME]?.let {
        addLibraryEntry(roots, it)
      }
    }
    return module
  }

private fun addLibraryEntry(roots: ModifiableRootModel, library: Library) =
    roots.addLibraryEntry(library).apply {
      setScope(DependencyScope.COMPILE)
      setExported(false)
    }

  @JvmStatic
  fun getModulesForModels(
    spec: ProjectProto.Project,
    models: IdeModifiableModelsProvider,
    imlDirectory: File,
    projectPathResolver: ProjectPath.Resolver,
    workspaceRoot: WorkspaceRoot,
    libMap: ImmutableMap<String, Library>
  ): Pair<IdeModifiableModelsProvider, List<Pair<Module, ProjectProto.Module>>> {
    val modules = spec.modulesList.map { moduleSpec ->
      val module = getModuleForModuleSpec(moduleSpec, models, imlDirectory, projectPathResolver, workspaceRoot, libMap)
      module to moduleSpec
    }
    return models to modules
  }

  @JvmStatic
  fun updateProjectStructureForQuerySync(
    project: Project,
    context: Context<*>,
    models: IdeModifiableModelsProvider,
    modules: List<Pair<Module, ProjectProto.Module>>,
    workspaceRoot: WorkspaceRoot,
    workspaceLanguageSettings: WorkspaceLanguageSettings,
    syncPlugin: BlazeQuerySyncPlugin
  ) {
    for ((module, moduleSpec) in modules) {
      syncPlugin.updateProjectStructureForQuerySync(
        project,
        context,
        models,
        workspaceRoot,
        module,
        ImmutableSet.copyOf(moduleSpec.getAndroidResourceDirectoriesList()),
        ImmutableSet.Builder<String>()
          .addAll(moduleSpec.androidSourcePackagesList)
          .addAll(moduleSpec.androidCustomPackagesList)
          .build(),
        workspaceLanguageSettings
      )
    }
  }
}
