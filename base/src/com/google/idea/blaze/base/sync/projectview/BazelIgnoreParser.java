package com.google.idea.blaze.base.sync.projectview;


import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.io.VfsUtils;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import javax.annotation.Nullable;

public class BazelIgnoreParser {

  Logger logger = Logger.getInstance(BazelIgnoreParser.class);

  @Nullable
  private final File bazelIgnoreFile;

  public BazelIgnoreParser(WorkspaceRoot workspaceRoot) {
    this.bazelIgnoreFile = workspaceRoot.fileForPath(new WorkspacePath(".bazelignore"));
  }

  public ImmutableList<WorkspacePath> getIgnoredPaths() {
    VirtualFile vf = VfsUtils.resolveVirtualFile(bazelIgnoreFile);
    if (vf == null || !vf.exists()) {
      return ImmutableList.of();
    }

    Document document = FileDocumentManager.getInstance().getDocument(vf);
    if (document == null || document.getImmutableCharSequence().length() == 0)  {
      return ImmutableList.of();

    }

    ImmutableList.Builder<WorkspacePath> ignoredPaths = ImmutableList.builder();

    for (CharSequence cs : Splitter.on("\n").split(document.getImmutableCharSequence())) {
      String path = cs.toString();
      String validationOutcome = WorkspacePath.validate(path);
      if (validationOutcome != null) {
        if (validationOutcome.contains("may not end with '/'")) {
          path = path.substring(0, path.length() - 1);
        } else {
          logger.warn(
              "Found "
                  + path
                  + " in .bazelignore, but unable to parse as relative workspace path for reason: "
                  + validationOutcome);
          continue;
        }
      }

      ignoredPaths.add(new WorkspacePath(path));
    }

    return ignoredPaths.build();
  }

}
