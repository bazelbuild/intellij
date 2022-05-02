/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.libraries;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.command.buildresult.BlazeArtifact;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.sync.libraries.LintCollector;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.java.sync.model.BlazeJavaSyncData;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import javax.annotation.Nullable;

/** {@inheritDoc} Collecting lint rule jars from {@code BlazeJavaSyncData} */
public class JavaLintCollector implements LintCollector {
  private static final Logger logger = Logger.getInstance(JarCache.class);

  private static final String ISSUE_REGISTRY_LOADER_RESOURCE_NAME =
      "META-INF/services/com.android.tools.lint.client.api.IssueRegistry";
  private static final String PLUGIN_ISSUE_REGISTRY =
      "com.google.android.tools.lint.registration.ServiceLoadingIssueRegistry";

  public static ImmutableList<BlazeArtifact> collectLintJarsArtifacts(
      BlazeProjectData blazeProjectData) {
    BlazeJavaSyncData syncData = blazeProjectData.getSyncState().get(BlazeJavaSyncData.class);
    ArtifactLocationDecoder artifactLocationDecoder = blazeProjectData.getArtifactLocationDecoder();

    if (syncData == null) {
      return ImmutableList.of();
    }
    return syncData.getImportResult().pluginProcessorJars.stream()
        .map(artifactLocationDecoder::resolveOutput)
        .collect(toImmutableList());
  }

  @Override
  public ImmutableList<File> collectLintJars(Project project, BlazeProjectData blazeProjectData) {
    JarCache jarCache = JarCache.getInstance(project);
    FileOperationProvider fileOperationProvider = FileOperationProvider.getInstance();
    ImmutableList<File> jars =
        collectLintJarsArtifacts(blazeProjectData).stream()
            .map(jarCache::getCachedJar)
            .filter(jar -> (jar != null) && fileOperationProvider.exists(jar))
            .collect(toImmutableList());
    try {
      File lintChecksJar =
          makeLintChecksJar(JarCacheFolderProvider.getInstance(project).getJarCacheFolder(), jars);
      return lintChecksJar == null ? ImmutableList.of() : ImmutableList.of(lintChecksJar);
    } catch (IOException e) {
      logger.warn("Fail to zip lint jars", e);
      return jars;
    }
  }

  /**
   * Create a lint jar that can be used to load all plugin jars. This is a temp workaround until
   * b/203563664 get fixed. This piece of code follows {@code
   * com.google.devtools.jvmtools.lint.runner.AndroidLintRunner#makeLintChecksJar}
   */
  @Nullable
  static File makeLintChecksJar(File dir, List<File> pluginJars) throws IOException {
    if (pluginJars.isEmpty()) {
      return null;
    }

    // To avoid merging plugin Jars we make a Jar with "Class-Path" manifest entry. IntelliJ's
    // classloader expects that Jar to be named "classpath...jar" and contain absolute file URIs.
    Path result = dir.toPath().resolve("classpath.jar");
    Manifest manifest = new Manifest();
    // Must set Manifest-Version, otherwise empty manifest is written :(
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    manifest
        .getMainAttributes()
        .put(
            Attributes.Name.CLASS_PATH,
            pluginJars.stream()
                .map(jar -> "file://" + jar.getAbsolutePath())
                .collect(joining(" ")));

    // Make sure Lint tries to load our Lint rule registry. IntelliJ's UrlClassLoader doesn't find
    // META-INF services files in Class-Path Jars, so reproduce in main Jar.
    // TODO(b/143231996): skip creating Jar when IssueRegistry not in classpath
    try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(result), manifest)) {
      jar.putNextEntry(new ZipEntry(ISSUE_REGISTRY_LOADER_RESOURCE_NAME));
      jar.write((PLUGIN_ISSUE_REGISTRY + "\n").getBytes(UTF_8));
      jar.closeEntry();
    }
    return result.toFile();
  }
}
