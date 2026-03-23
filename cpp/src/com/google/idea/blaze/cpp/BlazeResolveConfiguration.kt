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

private val DEFAULT_LANGUAGE_KIND = CLanguageKind.CPP

/** A clustering of "equivalent" Blaze targets for creating [OCResolveConfiguration].  */
data class BlazeResolveConfiguration(
  val configurationData: BlazeResolveConfigurationData,
  val displayName: String,
  val targets: ImmutableList<TargetKey>,
  val sources: ImmutableMap<TargetKey, ImmutableList<VirtualFile>>
) {

  companion object {
    @JvmStatic
    fun create(
      blazeProjectData: BlazeProjectData,
      configurationData: BlazeResolveConfigurationData,
      targets: Collection<TargetKey>
    ): BlazeResolveConfiguration = BlazeResolveConfiguration(
      configurationData,
      computeDisplayName(targets),
      ImmutableList.copyOf(targets),
      computeTargetToSources(blazeProjectData, targets)
    )
  }

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

  return buildString {
    append(minTargetKey.label())

    // on resolve configuration can cover multiple Bazel configurations with same compiler option
    if (minTargetKey.configuration().isNotBlank()) {
      append(" (${minTargetKey.configuration()})")
    }

    if (targets.size > 1) {
      append(" and ${targets.size - 1} other target(s)")
    }
  }
}

private fun computeTargetToSources(
  blazeProjectData: BlazeProjectData,
  targets: Collection<TargetKey>,
): ImmutableMap<TargetKey, ImmutableList<VirtualFile>> {
  val builder = ImmutableMap.builder<TargetKey, ImmutableList<VirtualFile>>()

  for (targetKey in targets) {
    builder.put(targetKey, computeSources(blazeProjectData, targetKey))
  }

  return builder.build()
}

private fun computeSources(
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
