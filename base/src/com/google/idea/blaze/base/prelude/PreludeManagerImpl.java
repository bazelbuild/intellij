package com.google.idea.blaze.base.prelude;

import com.google.idea.blaze.base.lang.buildfile.psi.BuildElement;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.references.BuildReferenceManager;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Processor;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

final public class PreludeManagerImpl extends PreludeManager {

  private static final String PRELUDE_FILE_NAME = "prelude_bazel";

  private final Project project;
  private PreludeFile preludeFile = null;
  private final AtomicBoolean initialized = new AtomicBoolean(false);

  public PreludeManagerImpl(Project project) {
    this.project = project;
  }

  @Override
  public boolean searchSymbolsInScope(Processor<BuildElement> processor,
      @Nullable PsiElement stopAtElement) {
    if (!initialized.getAndSet(true)) {
      reloadPreludeFile();
      subscribeToFileUpdates();
    }

    if (preludeFile != null) {
      return preludeFile.searchSymbolsInScope(processor, stopAtElement);
    } else {
      return true;
    }
  }

  private void subscribeToFileUpdates() {
    MessageBusConnection connection = project.getMessageBus().connect();
    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        boolean preludeUpdated = events.stream().anyMatch(f -> {
          VirtualFile updatedFile = f.getFile();
          boolean isPreludeFile =
              updatedFile != null && updatedFile.getName().endsWith(PRELUDE_FILE_NAME);
          return isPreludeFile && f.isFromSave() || f.isFromRefresh();
        });

        if (preludeUpdated) {
          reloadPreludeFile();
        }
      }
    });
  }

  private void reloadPreludeFile() {
    WorkspaceRoot projectRoot = WorkspaceRoot.fromProjectSafe(project);
    if (projectRoot == null) {
      return;
    }

    File preludeFileOnDisk = projectRoot
        .fileForPath(new WorkspacePath("tools/build_rules/" + PRELUDE_FILE_NAME));
    if (preludeFileOnDisk == null) {
      return;
    }

    PsiFileSystemItem preludeFile = BuildReferenceManager.getInstance(project)
        .resolveFile(preludeFileOnDisk);
    if (preludeFile != null) {
      this.preludeFile = new PreludeFile(ObjectUtils.tryCast(preludeFile, BuildFile.class));
    }
  }

}
