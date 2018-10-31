/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.sdkcompat.vcs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.Change.Type;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsUser;
import com.intellij.vcs.log.impl.VcsChangesLazilyParsedDetails;
import com.intellij.vcs.log.impl.VcsStatusDescriptor;
import java.util.List;
import javax.annotation.Nullable;

/** #api182: adapter for changes to VcsChangesLazilyParsedDetails in 2018.3. */
public abstract class VcsChangesLazilyParsedDetailsAdapter<V extends VcsRevisionNumber>
    extends VcsChangesLazilyParsedDetails {

  protected final V vcsRevisionNumber;

  protected VcsChangesLazilyParsedDetailsAdapter(
      Project project,
      VirtualFile root,
      Hash hash,
      List<Hash> parentsHashes,
      V vcsRevisionNumber,
      String subject,
      String commitMessage,
      VcsUser author,
      long time,
      List<List<FileStatusInfo>> reportedChanges) {
    super(hash, parentsHashes, time, root, subject, author, commitMessage, author, time);
    this.vcsRevisionNumber = vcsRevisionNumber;
    myChanges.set(
        reportedChanges.isEmpty() ? EMPTY_CHANGES : new UnparsedChanges(project, reportedChanges));
  }

  /** #api182: adapter for changes to VcsChangesLazilyParsedDetails in 2018.3. */
  public static class FileStatusInfo {

    private final Type type;
    private final String firstPath;
    private final String secondPath;

    public FileStatusInfo(Type type, String firstPath, @Nullable String secondPath) {
      this.type = type;
      this.firstPath = firstPath;
      this.secondPath = secondPath;
    }
  }

  protected abstract List<V> getParents(V revision);

  protected abstract Change createChange(
      Project project,
      VirtualFile root,
      @Nullable String fileBefore,
      @Nullable V revisionBefore,
      @Nullable String fileAfter,
      V revisionAfter,
      FileStatus aStatus);

  protected abstract FileStatus renamedFileStatus();

  private static class ChangesDescriptor extends VcsStatusDescriptor<FileStatusInfo> {
    @Override
    protected FileStatusInfo createStatus(
        Change.Type type, String path, @Nullable String secondPath) {
      return new FileStatusInfo(type, path, secondPath);
    }

    @Override
    public String getFirstPath(FileStatusInfo info) {
      return info.firstPath;
    }

    @Nullable
    @Override
    public String getSecondPath(FileStatusInfo info) {
      return info.secondPath;
    }

    @Override
    public Change.Type getType(FileStatusInfo info) {
      return info.type;
    }
  }

  private class UnparsedChanges
      extends VcsChangesLazilyParsedDetails.UnparsedChanges<FileStatusInfo> {
    private UnparsedChanges(Project project, List<List<FileStatusInfo>> changesOutput) {
      super(project, changesOutput, new ChangesDescriptor());
    }

    @Override
    protected List<Change> parseStatusInfo(List<FileStatusInfo> changes, int parentIndex) {
      List<Change> result = ContainerUtil.newArrayList();
      for (FileStatusInfo info : changes) {
        String filePath = info.firstPath;
        V parentRevision =
            getParents(vcsRevisionNumber).isEmpty()
                ? null
                : getParents(vcsRevisionNumber).get(parentIndex);
        switch (info.type) {
          case MODIFICATION:
            result.add(
                createChange(
                    myProject,
                    getRoot(),
                    filePath,
                    parentRevision,
                    filePath,
                    vcsRevisionNumber,
                    FileStatus.MODIFIED));
            break;
          case NEW:
            result.add(
                createChange(
                    myProject,
                    getRoot(),
                    null,
                    null,
                    filePath,
                    vcsRevisionNumber,
                    FileStatus.ADDED));
            break;
          case DELETED:
            result.add(
                createChange(
                    myProject,
                    getRoot(),
                    filePath,
                    parentRevision,
                    null,
                    vcsRevisionNumber,
                    FileStatus.DELETED));
            break;
          case MOVED:
            result.add(
                createChange(
                    myProject,
                    getRoot(),
                    filePath,
                    parentRevision,
                    info.secondPath,
                    vcsRevisionNumber,
                    renamedFileStatus()));
            break;
        }
      }
      return result;
    }
  }
}
