package com.google.idea.blaze.protoeditor;

import com.google.idea.blaze.base.command.buildresult.OutputArtifactResolver;
import com.google.idea.blaze.base.io.VfsUtils;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.protobuf.ide.settings.PbProjectSettings;
import com.intellij.protobuf.lang.resolve.FileResolveProvider;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

public class BlazeProtoFileResolver implements FileResolveProvider {
  @Override
  public @Nullable VirtualFile getDescriptorFile(@NotNull Project project) {
    return null;
  }

  @Override
  public @NotNull Collection<ChildEntry> getChildEntries(
      @NotNull String path, @NotNull Project project) {
    // no completion at the moment
    return Collections.emptyList();
  }

  @Override
  public @Nullable VirtualFile findFile(@NotNull String path, @NotNull Project project) {
    // Protocol Buffers plugin brings its own copy of google/protobuf/*.proto files
    // and attaches them to the project by default. In this case we don't need to resolve
    // the files because the multiple results will confuse the Protocol Buffers plugin.
    if (path.startsWith("google/protobuf")
        && PbProjectSettings.getInstance(project).isAutoConfigEnabled()) {
      return null;
    }
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      return null;
    }
    return BlazeProtoAdditionalLibraryRootsProvider.externalProtoArtifacts(projectData)
        .filter((location) -> location.getExecutionRootRelativePath().endsWith(path))
        .map(
            location ->
                OutputArtifactResolver.resolve(
                    project, projectData.getArtifactLocationDecoder(), location))
        .filter(Objects::nonNull)
        .map(file -> VfsUtils.resolveVirtualFile(file, false))
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  @Override
  public @NotNull GlobalSearchScope getSearchScope(@NotNull Project project) {
    return GlobalSearchScope.allScope(project);
  }
}
