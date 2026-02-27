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
package com.google.idea.blaze.cpp

import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.idea.blaze.base.ideinfo.TargetKey
import com.google.idea.blaze.base.io.VirtualFileSystemProvider
import com.google.idea.blaze.base.model.BlazeProjectData
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.cidr.lang.CLanguageKind
import com.jetbrains.cidr.lang.OCFileTypeHelpers
import com.jetbrains.cidr.lang.OCLanguageKind
import org.jetbrains.annotations.VisibleForTesting

private val DEFAULT_LANGUAGE_KIND = CLanguageKind.CPP

/** A clustering of "equivalent" Blaze targets for creating [OCResolveConfiguration].  */
data class BlazeResolveConfiguration(
  @VisibleForTesting val configurationData: BlazeResolveConfigurationData,
  val displayName: String,
  val targets: ImmutableList<TargetKey>,
  val sources: ImmutableMap<TargetKey, ImmutableList<VirtualFile>>
) {

  companion object {
    @JvmStatic
    fun create(
      project: Project,
      blazeProjectData: BlazeProjectData,
      configurationData: BlazeResolveConfigurationData,
      targets: Collection<TargetKey>
    ): BlazeResolveConfiguration = BlazeResolveConfiguration(
      configurationData,
      computeDisplayName(targets),
      ImmutableList.copyOf(targets),
      computeTargetToSources(project, blazeProjectData, targets)
    )
  }

  val compilerSettings: BlazeCompilerSettings get() = configurationData.compilerSettings()

  fun getSources(targetKey: TargetKey): ImmutableList<VirtualFile> {
    return sources[targetKey] ?: ImmutableList.of()
  }

  fun getDeclaredLanguageKind(project: Project, sourceOrHeaderFile: VirtualFile): OCLanguageKind? {
    val fileName = sourceOrHeaderFile.name
    if (OCFileTypeHelpers.isSourceFile(fileName)) {
      return getLanguageKind(sourceOrHeaderFile)
    }

    if (OCFileTypeHelpers.isHeaderFile(fileName)) {
      return getLanguageKind(SourceFileFinder.findAndGetSourceFileForHeaderFile(project, sourceOrHeaderFile))
    }

    return null
  }

  private fun getLanguageKind(sourceFile: VirtualFile?): OCLanguageKind {
    if (sourceFile == null) return DEFAULT_LANGUAGE_KIND

    val kind = OCFileTypeHelpers.getLanguageKind(sourceFile.name)
    return kind ?: DEFAULT_LANGUAGE_KIND
  }

}

private fun computeDisplayName(targets: Collection<TargetKey>): String {
  val minTargetKey = requireNotNull(targets.minOrNull())

  val name = String.format(
    "%s (%s)",
    minTargetKey.label().toString(),
    minTargetKey.configurationId().ifBlank { "unknown" },
  )

  return if (targets.size == 1) {
    name
  } else {
    String.format("%s and %d other target(s)", name, targets.size - 1)
  }
}

private fun computeTargetToSources(
  project: Project,
  blazeProjectData: BlazeProjectData,
  targets: Collection<TargetKey>,
): ImmutableMap<TargetKey, ImmutableList<VirtualFile>> {
  val builder = ImmutableMap.builder<TargetKey, ImmutableList<VirtualFile>>()

  for (targetKey in targets) {
    builder.put(targetKey, computeSources(project, blazeProjectData, targetKey))
  }

  return builder.build()
}

private fun computeSources(
  project: Project,
  blazeProjectData: BlazeProjectData,
  targetKey: TargetKey,
): ImmutableList<VirtualFile> {
  val builder = ImmutableList.builder<VirtualFile>()

  val ideInfo = blazeProjectData.targetMap()[targetKey]
  if (ideInfo?.getcIdeInfo() == null) {
    return ImmutableList.of()
  }

  for (source in ideInfo.sources) {
    val path = blazeProjectData.artifactLocationDecoder().decode(source).toPath()

    val virtualFile = VirtualFileSystemProvider.getInstance().system.findFileByNioFile(path)
    if (virtualFile == null || !OCFileTypeHelpers.isSourceFile(virtualFile.name)) {
      continue
    }

    builder.add(virtualFile)
  }

  return builder.build()
}
