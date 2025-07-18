package com.google.idea.blaze.clwb.base;

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.testing.headless.Assertions.abort;

import com.google.idea.blaze.base.bazel.BazelVersion;
import com.google.idea.testing.headless.HeadlessTestCase;
import com.google.idea.testing.headless.ProjectViewBuilder;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.CLanguageKind;
import com.jetbrains.cidr.lang.workspace.OCCompilerSettings;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.lang.workspace.OCWorkspace;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import org.jetbrains.annotations.Nullable;

public abstract class ClwbHeadlessTestCase extends HeadlessTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    setupSandboxBin();
  }

  @Override
  protected void tearDown() {
    final var roots = new ArrayList<AllowedVfsRoot>();
    addAllowedVfsRoots(roots);

    Assertions.assertVfsLoads(myBazelInfo.executionRoot(), roots);
  }

  private void setupSandboxBin() {
    final var clionId = PluginId.getId("com.intellij.clion");
    assertThat(clionId).isNotNull();

    final var clionPlugin = PluginManager.getInstance().findEnabledPlugin(clionId);
    assertThat(clionPlugin).isNotNull();

    var pluginsPath = clionPlugin.getPluginPath();
    while (pluginsPath != null && !pluginsPath.endsWith("plugins")) pluginsPath = pluginsPath.getParent();
    assertThat(pluginsPath).isNotNull();

    final var sdkBinPath = pluginsPath.getParent().resolve("bin");
    assertExists(sdkBinPath.toFile());

    try {
      Files.createSymbolicLink(Path.of(PathManager.getBinPath()), sdkBinPath);
    } catch (IOException e) {
      abort("could not create bin path symlink", e);
    }
  }

  protected void addAllowedVfsRoots(ArrayList<AllowedVfsRoot> roots) { }

  @Override
  protected ProjectViewBuilder projectViewText(BazelVersion version) {
    // required for Bazel 6 integration tests
    return super.projectViewText(version).addBuildFlag("--cxxopt=-std=c++17");
  }

  protected OCWorkspace getWorkspace() {
    return OCWorkspace.getInstance(myProject);
  }

  protected OCResolveConfiguration findFileResolveConfiguration(String relativePath) {
    final var file = findProjectFile(relativePath);

    final var configurations = getWorkspace().getConfigurationsForFile(file);
    assertThat(configurations).hasSize(1);

    return configurations.get(0);
  }

  protected OCCompilerSettings findFileCompilerSettings(String relativePath) {
    final var file = findProjectFile(relativePath);
    final var resolveConfiguration = findFileResolveConfiguration(relativePath);

    return resolveConfiguration.getCompilerSettings(CLanguageKind.CPP, file);
  }
}
