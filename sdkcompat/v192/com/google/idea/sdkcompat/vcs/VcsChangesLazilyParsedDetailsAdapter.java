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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.Change.Type;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsShortCommitDetails;
import com.intellij.vcs.log.VcsUser;
import com.intellij.vcs.log.impl.VcsChangesLazilyParsedDetails;
import com.intellij.vcs.log.impl.VcsFileStatusInfo;
import java.util.List;
import javax.annotation.Nullable;

/** #api182: adapter for changes to VcsChangesLazilyParsedDetails in 2018.3. */
public abstract class VcsChangesLazilyParsedDetailsAdapter<V extends VcsRevisionNumber>
    extends VcsChangesLazilyParsedDetails {

  /** #api191: adapter for changes in 2019.2 */
  public interface Helper<V extends VcsRevisionNumber> {
    FileStatus renamedStatus();

    ImmutableList<V> getParents(V revision);

    Change createChange(
        Project project,
        VirtualFile root,
        @Nullable String fileBefore,
        @Nullable V revisionBefore,
        @Nullable String fileAfter,
        V revisionAfter,
        FileStatus aStatus);
  }

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
      List<List<FileStatusInfo>> reportedChanges,
      Helper<V> helper) {
    super(
        project,
        hash,
        parentsHashes,
        time,
        root,
        subject,
        author,
        commitMessage,
        author,
        time,
        convert(reportedChanges),
        new VcsChangesParser<>(vcsRevisionNumber, helper));
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

  private static List<List<VcsFileStatusInfo>> convert(List<List<FileStatusInfo>> changes) {
    return changes.stream()
        .map(
            l ->
                l.stream()
                    .map(f -> new VcsFileStatusInfo(f.type, f.firstPath, f.secondPath))
                    .collect(toImmutableList()))
        .collect(toImmutableList());
  }

  private static class VcsChangesParser<V extends VcsRevisionNumber> implements ChangesParser {

    private final V revisionNumber;
    private final Helper<V> helper;

    VcsChangesParser(V revisionNumber, Helper<V> helper) {
      this.revisionNumber = revisionNumber;
      this.helper = helper;
    }

    @Override
    public List<Change> parseStatusInfo(
        Project project,
        VcsShortCommitDetails commit,
        List<VcsFileStatusInfo> changes,
        int parentIndex) {
      ImmutableList<V> parents = helper.getParents(revisionNumber);
      V parentRevision = parents.isEmpty() ? null : parents.get(parentIndex);
      List<Change> result = ContainerUtil.newArrayList();
      for (VcsFileStatusInfo info : changes) {
        String filePath = info.getFirstPath();
        switch (info.getType()) {
          case MODIFICATION:
            result.add(
                helper.createChange(
                    project,
                    commit.getRoot(),
                    filePath,
                    parentRevision,
                    filePath,
                    revisionNumber,
                    FileStatus.MODIFIED));
            break;
          case NEW:
            result.add(
                helper.createChange(
                    project,
                    commit.getRoot(),
                    null,
                    null,
                    filePath,
                    revisionNumber,
                    FileStatus.ADDED));
            break;
          case DELETED:
            result.add(
                helper.createChange(
                    project,
                    commit.getRoot(),
                    filePath,
                    parentRevision,
                    null,
                    revisionNumber,
                    FileStatus.DELETED));
            break;
          case MOVED:
            result.add(
                helper.createChange(
                    project,
                    commit.getRoot(),
                    filePath,
                    parentRevision,
                    info.getSecondPath(),
                    revisionNumber,
                    helper.renamedStatus()));
            break;
        }
      }
      return result;
    }
  }
}
