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
package com.google.idea.blaze.base.qsync;

import com.google.idea.blaze.base.command.buildresult.OutputArtifact;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.common.PrintOutput;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.io.ZipUtil;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.jetbrains.annotations.NotNull;

/** A local cache of project dependencies. */
public class DependencyCache {

  private final Project project;

  public DependencyCache(Project project) {
    this.project = project;
  }

  @NotNull
  private Path labelToLib(String target) throws IOException {
    Path libs = Paths.get(project.getBasePath()).resolve(".blaze/libraries");
    Files.createDirectories(libs);
    return libs.resolve(labelToFile(target));
  }

  private String labelToFile(String label) {
    return label.replaceAll("/", "_").replace(":", "_") + label.hashCode() + ".jar";
  }

  private String labelToAarDir(String label) {
    return label.replace('/', '_').replace(':', '_');
  }

  private void unzipAar(String dirName, ZipInputStream parentStream) throws IOException {
    Path libs = Paths.get(project.getBasePath()).resolve(".blaze/libraries");
    Path aarDir = libs.resolve(dirName);
    // TODO: decompress via ZipInputStream so we don't require a local file
    Path tmpDir = Files.createTempDirectory(dirName);
    tmpDir.toFile().deleteOnExit();
    Files.createDirectories(aarDir);
    Files.copy(parentStream, tmpDir.resolve(dirName), StandardCopyOption.REPLACE_EXISTING);
    ZipUtil.extract(tmpDir.resolve(dirName), aarDir, (dir, name) -> !name.endsWith(".jar"));
    project.getService(AarDependencyRegistry.class).registerAar(dirName);
  }

  public ArrayList<File> addArchive(BlazeContext context, OutputArtifact zip) throws IOException {
    ArrayList<File> newFiles = new ArrayList<>();
    long now = System.nanoTime();
    int total = 0;
    int skipped = 0;
    try (InputStream lis = zip.getInputStream();
        ZipInputStream zis = new ZipInputStream(lis)) {
      ZipEntry entry = null;
      while ((entry = zis.getNextEntry()) != null) {
        if (entry.getName().endsWith(".aar")) {
          unzipAar(labelToAarDir(entry.getName()), zis);
          total++;
          continue;
        }
        Path libFile = labelToLib(entry.getName());
        total++;
        if (Files.exists(libFile)) {
          skipped++;
          continue;
        }
        Files.copy(zis, libFile, StandardCopyOption.REPLACE_EXISTING);
        newFiles.add(libFile.toFile());
      }
      // TODO: If the last entry is an AAR file, the input stream will be closed after that AAR is
      // unzipped.
    } catch (IOException e) {
      Logger.getInstance(DependencyCache.class).warn(e);
    }
    long elapsedMs = (System.nanoTime() - now) / 1000000L;
    context.output(
        PrintOutput.log(
            String.format(
                "Copied %d (skipped %d total %d) artifacts in %d ms",
                total - skipped, skipped, total, elapsedMs)));
    return newFiles;
  }
}
