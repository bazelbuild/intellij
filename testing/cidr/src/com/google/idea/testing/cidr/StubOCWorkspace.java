package com.google.idea.testing.cidr;

import com.google.common.collect.ImmutableList;
import com.google.idea.sdkcompat.cidr.OCWorkspaceAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.OCFileType;
import com.jetbrains.cidr.lang.symbols.OCSymbol;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

/** A stub {@link com.jetbrains.cidr.lang.workspace.OCWorkspace} to use for testing. */
class StubOCWorkspace extends OCWorkspaceAdapter {

  private final List<OCResolveConfiguration> resolveConfigurations;

  StubOCWorkspace(Project project) {
    super(project);
    resolveConfigurations = new ArrayList<>();
    resolveConfigurations.add(new StubOCResolveConfiguration(project));
  }

  @Override
  public Collection<VirtualFile> getLibraryFilesToBuildSymbols() {
    return ImmutableList.of();
  }

  @Override
  public boolean areFromSameProject(@Nullable VirtualFile a, @Nullable VirtualFile b) {
    return Objects.equals(a, b);
  }

  @Override
  public boolean areFromSamePackage(@Nullable VirtualFile a, @Nullable VirtualFile b) {
    return Objects.equals(a, b);
  }

  @Override
  public boolean isInSDK(@Nullable VirtualFile file) {
    return false;
  }

  @Override
  public boolean isFromWrongSDK(OCSymbol symbol, @Nullable VirtualFile contextFile) {
    return false;
  }

  @Override
  public List<? extends OCResolveConfiguration> getConfigurations() {
    return resolveConfigurations;
  }

  @Override
  public List<? extends OCResolveConfiguration> getConfigurationsForFile(
      @Nullable VirtualFile sourceFile) {
    if (sourceFile == null) {
      return Collections.emptyList();
    }
    return OCFileType.INSTANCE.equals(sourceFile.getFileType())
        ? resolveConfigurations
        : Collections.emptyList();
  }
}
