/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.buildmodifier;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.TestModeFlags;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.download.DownloadableFileService;
import com.intellij.util.system.CpuArch;
import com.intellij.util.system.OS;
import com.intellij.util.ui.EDT;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

public final class BuildifierDownloader {

  private static final Logger LOG = Logger.getInstance(BuildifierDownloader.class);

  /**
   * Key for {@link TestModeFlags} to mock the OS version.
   */
  static final Key<OS> OS_KEY = new Key<>("OS_KEY");

  /**
   * Key for {@link TestModeFlags} to mock the CpuArch.
   */
  static final Key<CpuArch> CPU_ARCH_KEY = new Key<>("CPU_ARCH_KEY");

  /**
   * Key for {@link TestModeFlags} to mock the download location.
   */
  static final Key<Path> DOWNLOAD_PATH_KEY = new Key<>("DOWNLOAD_PATH_KEY");

  /**
   * Hardcoded buildifier version, update it to bump the version.
   */
  private static final String BUILDIFIER_VERSION = "7.1.2";

  private static final Path DOWNLOAD_PATH = PathManager.getPluginsDir().resolve("buildifier");
  private static final String DOWNLOAD_URL = "https://github.com/bazelbuild/buildtools/releases/download";

  public static boolean canDownload() {
    return getDownloadUrl() != null;
  }

  public static @Nullable File downloadWithProgress(Project project) {
    return ProgressManager.getInstance().runProcessWithProgressSynchronously(
        BuildifierDownloader::downloadSync,
        "Downloading: " + getFileName(),
        true,
        project
    );
  }

  @VisibleForTesting
  public static @Nullable File downloadSync() {
    try {
      return download();
    } catch (Exception e) {
      LOG.error("download failed", e);
      return null;
    }
  }

  private static File download() throws IOException {
    LOG.assertTrue(!EDT.isCurrentThreadEdt(), "runs on background thread");
    LOG.assertTrue(!ApplicationManager.getApplication().isReadAccessAllowed(), "runs without read lock");

    final var path = getDownloadPath();
    if (!Files.exists(path)) {
      Files.createDirectories(path);
    }

    final var fileName = getFileName();
    final var file = path.resolve(fileName);
    if (Files.exists(file)) {
      return file.toFile();
    }

    final var url = getDownloadUrl();
    if (url == null) {
      throw new IOException("cannot create download url");
    }

    final var service = DownloadableFileService.getInstance();
    final var description = service.createFileDescription(url, fileName);
    final var downloader = service.createDownloader(List.of(description), fileName);

    final var results = downloader.download(DOWNLOAD_PATH.toFile());
    final var result = ContainerUtil.getFirstItem(results);
    if (result == null) {
      throw new IOException("download failed");
    }

    final var resultFile = result.getFirst();
    FileUtil.setExecutable(resultFile);

    return resultFile;
  }

  private static Path getDownloadPath() {
    return ObjectUtils.coalesce(
        TestModeFlags.get(DOWNLOAD_PATH_KEY),
        PathManager.getPluginsDir().resolve("buildifier")
    );
  }

  private static String getFileName() {
    final var version = BUILDIFIER_VERSION.replace('.', '_');

    if (getOS() == OS.Windows) {
      return String.format("buildifier_%s.exe", version);
    } else {
      return String.format("buildifier_%s", version);
    }
  }

  private static OS getOS() {
    return ObjectUtils.coalesce(TestModeFlags.get(OS_KEY), OS.CURRENT);
  }

  private static CpuArch getCpuArch() {
    return ObjectUtils.coalesce(TestModeFlags.get(CPU_ARCH_KEY), CpuArch.CURRENT);
  }

  private static @Nullable String getPlatformIdentifier() {
    return switch (getOS()) {
      case Windows -> "windows";
      case macOS -> "darwin";
      case Linux -> "linux";
      default -> null;
    };
  }

  private static @Nullable String getArchitectureIdentifier() {
    return switch (getCpuArch()) {
      case X86_64 -> "amd64";
      case ARM64 -> "arm64";
      default -> null;
    };
  }

  private static @Nullable String getDownloadUrl() {
    final var platform = getPlatformIdentifier();
    final var arch = getArchitectureIdentifier();

    if (platform == null || arch == null) {
      return null;
    }

    // example: https://github.com/bazelbuild/buildtools/releases/download/v7.1.2/buildifier-darwin-amd64
    final var url = String.format(
        "%s/v%s/buildifier-%s-%s",
        DOWNLOAD_URL,
        BUILDIFIER_VERSION,
        platform,
        arch
    );

    if (getOS() == OS.Windows) {
      return url + ".exe";
    } else {
      return url;
    }
  }
}
